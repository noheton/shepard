---
layout: default
title: Container annotations (reference)
permalink: /reference/container-annotations/
audience: user
---
# Container annotations

Semantic annotations on a container itself — distinct from annotations
on a timeseries channel inside the container, on a data reference that
points at the container, or on the collection / data object that holds
the reference.

Use them to tag an entire container with provenance ("this is the
LUMEN-3 telemetry stream"), instrument metadata ("Brüel & Kjær LAN-XI
front-end"), or domain classification ("rocket-engine hot-fire
campaign"). The annotation is visible on the container detail page and
queryable through the standard semantic-annotation surfaces.

## Where annotations live

shepard already supported semantic annotations on every other primitive:

| Annotated thing | Endpoint root |
|---|---|
| Collection | `/shepard/api/collections/{id}/semanticAnnotations` |
| DataObject | `/shepard/api/collections/{cid}/dataObjects/{did}/semanticAnnotations` |
| Reference (Timeseries / File / SD / Git / Video) | `/shepard/api/collections/{cid}/dataObjects/{did}/basicReferences/{rid}/semanticAnnotations` |
| Timeseries *channel* (`TimeseriesEntity`) | `/shepard/api/timeseriesContainers/{cid}/timeseries/{tid}/semanticAnnotations` |
| **Container** (this fork, SA-CONT) | `/v2/{kind}-containers/{id}/annotations` |

Container annotations are the missing piece. The Neo4j relationship
(`:has_annotation`) already existed on every `BasicContainer` — only
the REST surface was missing.

## Endpoints

All three container kinds expose the same shape:

| Method | Path | Effect |
|---|---|---|
| `GET` | `/v2/{kind}-containers/{id}/annotations` | List annotations on the container |
| `POST` | `/v2/{kind}-containers/{id}/annotations` | Add a new annotation |
| `DELETE` | `/v2/{kind}-containers/{id}/annotations/{annotationId}` | Remove an annotation |

with `{kind}` ∈ `{timeseries, file, structured-data}`.

### Auth

- `GET` requires Read permission on the container.
- `POST` / `DELETE` require Write permission on the container.

### Bodies

The wire shape is the same `SemanticAnnotationIO` used everywhere
else in shepard:

```json
{
  "propertyIri": "http://purl.obolibrary.org/obo/IAO_0000136",
  "propertyName": "is about",
  "propertyRepositoryId": 1,
  "valueIri": "http://example.dlr.de/lumen3#hotfire-test-campaign-q3-2024",
  "valueName": "LUMEN-3 hot-fire campaign Q3 2024",
  "valueRepositoryId": 1
}
```

The autocomplete in the UI's add-annotation dialog (`N1e`) suggests
both the property and the value from any loaded ontology.

## Frontend integration

The container detail pages (`/containers/timeseries/{id}`,
`/containers/files/{id}`, `/containers/structureddata/{id}`) each
include a **Semantic Annotations** expansion panel, open by default
on the timeseries container, available on the others.

Internally the panel uses the standard `SemanticAnnotationList` +
`AddAnnotationButton` components with one of three new `Annotated`
classes:

- `AnnotatedTimeseriesContainer(containerId)`
- `AnnotatedFileContainer(containerId)`
- `AnnotatedStructuredDataContainer(containerId)`

Each is a thin wrapper that talks the new `/v2/` endpoints via
`useAuth()` — the generated OpenAPI client doesn't cover them yet
(it will at the next regeneration cycle).

## Search and discovery

Container annotations participate in the standard semantic search
surfaces:

- The `/v2/semantic/terms/search?q=…` autocomplete (`N1e`) suggests
  terms from any annotation on any entity, including containers.
- The future `/v2/semantic/{repoAppId}/sparql` proxy (`N1f`) can
  query container-attached annotations the same way it queries
  data-object annotations — they live in the same n10s graph.

## Related

- [Container safe-delete](/reference/container-safe-delete/) — the
  other new `/v2/` surface on container detail pages.
- [Semantic repositories](/reference/semantic-repositories/) — how
  the IRI → label resolution layer works.
