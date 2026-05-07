---
layout: default
title: User guide
description: Concepts an end user needs to navigate shepard — collections, data objects, references, permissions, search.
---

This page introduces the concepts you encounter while using shepard. It is
descriptive, not exhaustive — the live API explorer at
`/shepard/doc/swagger-ui` is the source of truth for fields and parameters.

## Collections

A **Collection** is the top-level container. It carries a name, a description,
free-form `attributes`, an owner, and a Permissions record. Everything you
store lives inside a Collection.

<figure class="screenshot-placeholder" data-target="collections-overview">
  <figcaption>Screenshot: Collections page — placeholder. Replace with Playwright capture once the visual-regression workflow lands.</figcaption>
</figure>

REST surface: `CollectionRest`
(`backend/.../context/collection/endpoints/CollectionRest.java`).

## DataObjects

A **DataObject** lives inside a Collection. DataObjects form two relationships:

- **parent / child** — composition. A parent DataObject groups its children;
  children inherit nothing automatically, but the tree shape is what the UI and
  most clients walk.
- **predecessor / successor** — derivation. A DataObject can declare one or
  more predecessor DataObjects (`predecessorIds`) under the same Collection,
  marking "this was derived from those".

REST surface: `DataObjectRest`
(`backend/.../context/collection/endpoints/DataObjectRest.java`).

## References

A **Reference** is a typed pointer attached to a DataObject. The reference
kinds verified in source today (under
`backend/src/main/java/de/dlr/shepard/context/references/.../endpoints/`):

| Kind | Class | Stores |
|---|---|---|
| File | `FileReferenceRest` | Files (binary), backed by MongoDB GridFS |
| Structured-Data | `StructuredDataReferenceRest` | JSON documents, MongoDB |
| Timeseries | (under `data/timeseries`, see `TimeseriesRest`) | Channels in TimescaleDB |
| Spatial-Data | `SpatialDataReferenceRest` | Geometries in PostGIS (optional) |
| URI | `URIReferenceRest` | External URIs |
| Lab-Journal | (under `context/labJournal`) | Rich-text lab entries |
| Collection / DataObject | (cross-references) | Links between entities |

## Permissions

Per `backend/src/main/java/de/dlr/shepard/auth/permission/model/Permissions.java`
the model is:

- An **owner** (single `User`).
- Lists of **manager**, **writer**, and **reader** users.
- Lists of **readerGroups** and **writerGroups** (`UserGroup`).
- A `permissionType` from
  `backend/src/main/java/de/dlr/shepard/common/util/PermissionType.java`:
  `Public`, `PublicReadable`, or `Private`.

Effective access is therefore:

- Owner — full control, including delete and permission changes.
- Manager — same as Owner except cannot reassign ownership.
- Writer — read + write (and via group writer, read + write).
- Reader — read-only (and via group reader, read-only).
- `PublicReadable` — anyone authenticated may read; only listed writers/managers
  may modify.
- `Public` — broader read; specifics governed by the visibility flag.
- `Private` — only the explicit lists apply.

## Search

A REST search endpoint exists at `SearchRest`
(`backend/.../common/search/endpoints/SearchRest.java`). A unified
`/search/v2` surface and richer query semantics are **proposed** in `aidocs/13`
and are not yet shipped — use the existing endpoints documented at
`/shepard/doc/swagger-ui` until the v2 design lands.

## API explorer

For exact request and response shapes, point your browser at
`https://your-host/shepard/doc/swagger-ui` (path confirmed from
`application.properties`: `quarkus.http.non-application-root-path=/shepard/doc`,
`quarkus.swagger-ui.always-include=true`). The OpenAPI document itself is at
`/shepard/doc/openapi.json`.
