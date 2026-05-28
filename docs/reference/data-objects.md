---
layout: default
title: Data Objects (reference)
permalink: /reference/data-objects/
---

# Data Objects

A `DataObject` is a node in a Collection's content graph: an
individual experimental run, manufacturing step, simulation case, or
publication artefact. DataObjects compose into provenance chains via
`Predecessor` / `Successor` / `Parent` / `Child` relationships, and
they own typed `References` to payload containers (timeseries,
files, structured data, video).

Both the upstream-compatible `/shepard/api/collections/{id}/dataObjects`
surface and this fork's `/v2/collections/{appId}/data-objects` surface
are available; the new fields described below are additive.

## Core fields

| Field | Type | Description |
|---|---|---|
| `id` | `long` | Neo4j-internal identifier. |
| `appId` | `string` | UUID v7. L2 native identifier. |
| `name` | `string` | Required. |
| `description` | `string` | Optional rich-text body. |
| `status` | `string` (enum) | `DRAFT` \| `IN_REVIEW` \| `READY` \| `PUBLISHED` \| `ARCHIVED`. |
| `attributes` | `Map<String, String>` | Free-text key-value annotations. |
| `collectionId` | `long` | The owning collection. |
| `parentId` | `long` (nullable) | Parent in the hierarchy. |
| `predecessorIds` / `successorIds` | `long[]` | Lineage chain (e.g. TR-004 → TR-005). |
| `childrenIds` | `long[]` | Direct children (e.g. anomaly-investigation sub-tree). |
| `referenceIds` | `long[]` | Payload references (NOT DataObject ids — distinct types). |
| `timeseriesReferenceCount`, `fileBundleCount`, `structuredDataReferenceCount`, `videoStreamReferenceCount` | `int` | Server-computed counts. |

## Fork additions (LIC1 — FAIR-1)

| Field | Type | Default | Description |
|---|---|---|---|
| `license` | `string` | `null` | SPDX license identifier (e.g. `CC-BY-4.0`, `MIT`, `Apache-2.0`, `PROPRIETARY`). |
| `accessRights` | `string` (enum) | `null` | `OPEN` \| `RESTRICTED` \| `CLOSED` \| `EMBARGOED`. |

Both fields use `@JsonInclude(NON_NULL)` so they are absent from the
wire when unset, preserving byte-compatibility with upstream v5.2.0
clients. The backend stores them as plain String — enum enforcement
is currently client-side (the v-select in the create / edit dialogs).

See `collections.md` for the full vocabulary description: it applies
verbatim to DataObjects.

## Why both Collection AND DataObject carry these fields

Funder review (DFG, EU Horizon Europe, Clean Aviation JU) requires
a `dcterms:license` and `dcat:accessRights` at the dataset granularity
the funder considers atomic. Some projects publish at Collection
granularity (one license for the whole bucket); others publish at
DataObject granularity (per-run licensing, common when a test
campaign has mixed-IP runs). The data model supports both:

- **Collection-level only**: leave DataObject fields `null`; the
  Collection's license applies transitively to all members.
- **DataObject-level override**: set the field on the specific run.
  When both are set, the DataObject value wins for that node.

The DataCite / RO-Crate export shape (see `aidocs/72`) consumes
DataObject-level values when present, falling back to Collection
defaults.

## Setting license and access rights

### REST

Both fields ride on the standard update payload:

```bash
curl -X PUT \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "TR-004",
    "license": "PROPRIETARY",
    "accessRights": "CLOSED"
  }' \
  https://shepard.example.org/v2/collections/$COLLECTION_APPID/data-objects/$DO_APPID
```

### UI

In the Edit Data Object dialog the two fields appear alongside
description and status. The Create Data Object dialog exposes them
in step 2 (Attributes). Both forms use:

- `LicenseInput` — v-autocomplete with SPDX suggestions; free-text
  override allowed.
- `AccessRightsInput` — v-select strictly limited to the four enum
  values; clearing the field sets `null`.

On the Data Object detail page both values render as small chips
below the title when set.

## Time-bounds sparklines in the DataObjects list

When a DataObject has `timeBoundsStart` and/or `timeBoundsEnd` set, the
DataObjects panel on the Collection landing page renders a small SVG bar in the
row, scaled across the collection's time window. Hovering the bar shows the
human-readable start–end range.

The sparkline column appears automatically when at least one DataObject in the
list carries time-bound metadata. DataObjects without bounds render an empty slot
in that column — they stay fully usable; the column just gives a quick visual
overview of when each run's data was recorded.

`timeBoundsStart` / `timeBoundsEnd` are ISO 8601 timestamps. Set them
programmatically on PUT/PATCH or via the Edit dialog in the UI (they appear in
the "Attributes" section of the dialog). Shepard does **not** infer them from
timeseries payload automatically — set them explicitly.

## metadata4ing (m4i) JSON-LD projection (M4I-c + M4I-d)

DataObjects ship a NFDI4Ing-canonical `metadata4ing` (m4i 1.4.0)
projection that any RDF-aware client (Apache Jena, RDFLib, pyShacl,
ROBOT, the NFDI4Ing Terminology Service) can read directly.
Request via content negotiation:

```bash
curl -H 'Accept: application/ld+json; profile="https://w3id.org/nfdi4ing/metadata4ing/"' \
     -H "X-API-KEY: $KEY" \
     https://shepard.example.dlr.de/v2/collections/<collection-appid>/data-objects/<do-appid>
```

The short profile form `Accept: application/ld+json; profile=metadata4ing`
also works. Without the `profile=` parameter, the canonical JSON
shape (see the table above) is returned unchanged.

The m4i body always carries the following mandatory triples:

| Predicate | Source | Notes |
|---|---|---|
| `@type` (`m4i:InvestigatedObject`, `prov:Entity`) | const | Dual-typed so PROV-O readers also parse. |
| `dcterms:identifier` | DataObject.appId | UUID v7. |
| `dcterms:title` | DataObject.name | Required, non-blank. |
| `schema:dateCreated` | DataObject.createdAt | `xsd:dateTime`. |

Optional triples — emitted when the underlying data carries them:

| Predicate | Source | Notes |
|---|---|---|
| `dcterms:description` | DataObject.description | CommonMark, surfaced as plain string. |
| `m4i:hasIdentifier` | KIP1a `Publication.pid` | A nested blank node with `m4i:identifierValue` + `m4i:hasIdentifierType "Handle"`. |
| `obo:RO_0002233` | Predecessors | `has input` per OBO Relations Ontology; multi-valued. |
| `obo:RO_0002234` | Successors | `has output`; multi-valued. |
| `prov:wasGeneratedBy` | Most-recent `:Activity` targeting this DO | Single. |
| `m4i:realizesMethod` | Activity.actionKind | `shepard:method/<kind>` IRI minted on the fly by `MethodResolver`. |
| `m4i:hasEmployedTool` | Activity.targetKind | `shepard:tool/<kind>` minted by `ToolResolver`. |
| `m4i:hasNumericalVariable` | Numeric `SemanticAnnotation`s | Blank nodes carrying `m4i:hasValue` + `qudt:unit`. |
| `schema:keywords` | Text `SemanticAnnotation`s | Free-text fallback for non-numeric annotations. |

The SHACL contract that pins this shape ships at
`backend/src/main/resources/shapes/m4i-dataobject-shape.ttl`. Validate
a live instance against it with the acceptance script:

```bash
pip install pyshacl rdflib requests
python3 examples/mffd-showcase/scripts/validate_m4i_shape.py \
    --shepard-url https://shepard.example.dlr.de \
    --api-key "$KEY" \
    --collection-id <collection-appid>
```

Unknown `profile=` value returns RFC 7807 problem+json with type
`https://noheton.github.io/shepard/errors/dataobject.unsupported-profile`
and status 406.

**Design source.** `aidocs/semantics/94 §4.3 / §4.4 / §12`.

## See also

- `collections.md` — sibling page documenting the same fields at the
  Collection level.
- `aidocs/semantics/98-shapes-views-and-process-model.md §4.1` — funder-review
  rationale and the deferred items.
- `provenance.md` — Predecessor/Successor chain semantics.
- `nfdi4ing-federation.md` — m4i federation runbook (M4I-e).
