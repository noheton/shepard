---
title: Semantic annotations (reference)
description: Complete reference for the SEMA-V6 annotation surface — data model, REST API, MCP tools, SPARQL, and vocabulary management
permalink: /reference/semantic-annotations/
layout: default
audience: user
---
# Semantic annotations (reference)

Semantic annotations attach machine-readable **property–value** assertions to any
Shepard entity: collections, data objects, references, containers, and
anything else that carries an `appId`. They live in Neo4j as
`:SemanticAnnotation` nodes and are queryable through the REST API,
MCP tools, and the SPARQL proxy.

---

## 1. What annotations are (and are not)

**Annotations** link an entity to a term from a controlled vocabulary or
to a free-text literal:

```
<entity appId> → predicateIri → objectIri | objectLiteral
```

Examples:
- `<TR-004>  schema:description  "Post-anomaly root-cause investigation"`
- `<TR-004>  prov:wasAttributedTo  <operator-orcid:0000-0002-1825-0097>`
- `<TR-004>  chameo:hasMeasurementPrinciple  chameo:VibrometryMeasurementPrinciple`

**The `attributes` bag is not the same thing.** The legacy `attributes`
map on DataObjects is a write-disabled escape hatch for pre-SEMA-V6
key-value pairs. New code should never write to it; all new metadata
goes through `:SemanticAnnotation`. Read [data-objects reference](/reference/data-objects/)
for the attribute-vs-annotation distinction.

---

## 2. Data model

Each `:SemanticAnnotation` node carries these properties:

| Property | Type | Notes |
|---|---|---|
| `appId` | UUID v7 | Stable identifier; use this in API calls |
| `subjectAppId` | UUID v7 | `appId` of the annotated entity |
| `subjectKind` | String | Entity label: `Collection`, `DataObject`, `TimeseriesContainerReference`, … |
| `predicateIri` | String | Full IRI of the predicate term |
| `predicateLabel` | String? | Human-readable label (optional, from vocabulary) |
| `objectIri` | String? | IRI of the object (XOR with `objectLiteral`) |
| `objectLiteral` | String? | Free-text literal value (XOR with `objectIri`) |
| `vocabularyId` | UUID v7? | `appId` of the `:Vocabulary` node the predicate belongs to |
| `sourceMode` | Enum | `human` (default) / `ai` / `collaborative` |
| `sourceActivityAppId` | UUID v7? | `appId` of the `:Activity` node that created this annotation |
| `validFromMillis` | Long? | Epoch-millis; null = "valid since creation" |
| `validUntilMillis` | Long? | Epoch-millis; null = "no expiry" |
| `confidence` | Float? | 0.0–1.0; null = "not specified"; human writes default to 1.0 |

Exactly one of `objectIri` or `objectLiteral` must be non-null.

---

## 3. REST API

Base path: `/v2/annotations`

### 3.1 List annotations

```
GET /v2/annotations
```

Query parameters:

| Parameter | Type | Description |
|---|---|---|
| `subjectAppId` | UUID | Filter by annotated entity |
| `subjectKind` | String | Filter by entity type |
| `predicateIri` | String (URL-encoded) | Filter by predicate |
| `vocabId` | UUID | Filter by vocabulary |
| `page` | Integer | 0-based page (default 0) |
| `pageSize` | Integer | Items per page (default 20, max 200) |

Response `200 OK`:

```json
{
  "content": [
    {
      "appId": "019612ab-…",
      "subjectAppId": "019612aa-…",
      "subjectKind": "DataObject",
      "predicateIri": "http://purl.org/dc/terms/description",
      "predicateLabel": "description",
      "objectLiteral": "TR-004 anomaly investigation",
      "sourceMode": "human",
      "confidence": 1.0,
      "validFromMillis": null,
      "validUntilMillis": null
    }
  ],
  "totalElements": 42,
  "page": 0,
  "pageSize": 20
}
```

### 3.2 Text search

```
GET /v2/annotations/find?q=<text>&subjectKind=<kind>&page=<n>&pageSize=<n>
```

Returns annotations whose predicate label, object IRI, or object literal
contains the query string.

### 3.3 Get single annotation

```
GET /v2/annotations/{appId}
```

Response `200 OK`: single annotation object (shape as above).

### 3.4 Create annotation

```
POST /v2/annotations
Content-Type: application/json
```

Request body:

```json
{
  "subjectAppId": "019612aa-…",
  "subjectKind": "DataObject",
  "predicateIri": "http://purl.org/dc/terms/description",
  "predicateLabel": "description",
  "objectLiteral": "Post-anomaly root-cause investigation",
  "sourceMode": "human",
  "confidence": 0.95,
  "validFromMillis": 1748217600000,
  "validUntilMillis": null
}
```

Rules:
- Exactly one of `objectIri` or `objectLiteral` must be provided.
- `subjectAppId` and `subjectKind` are both required.
- `sourceMode` defaults to `human`.
- `confidence` defaults to `null` (unspecified).

Response `201 Created`: the created annotation object with its `appId`.

**AI writes**: if your request carries the header `X-AI-Agent: <model-id>`,
the server automatically sets `sourceMode` to `ai` and records the model
identifier in the associated `:Activity`.

### 3.5 Update annotation (merge-patch)

```
PUT /v2/annotations/{appId}
Content-Type: application/json
```

RFC 7396 merge-patch — send only the fields you want to change.
`subjectAppId` and `subjectKind` are immutable after creation.

Response `200 OK`: the updated annotation object.

### 3.6 Delete annotation

```
DELETE /v2/annotations/{appId}
```

Response `204 No Content`. Hard delete; no recovery.

### 3.7 Export as Turtle (OA-framed)

```
GET /v2/annotations/{appId}/export/turtle
Accept: text/turtle
```

Returns the annotation encoded as a
[W3C Web Annotation](https://www.w3.org/TR/annotation-model/) in Turtle:

```turtle
@prefix oa: <http://www.w3.org/ns/oa#> .

<urn:shepard:annotation:019612ab-…> a oa:Annotation ;
  oa:hasTarget <urn:shepard:dataobject:019612aa-…> ;
  oa:hasBody [
    a oa:TextualBody ;
    rdf:value "Post-anomaly root-cause investigation" ;
    dcterms:format "text/plain"
  ] ;
  oa:motivatedBy oa:describing ;
  dcterms:creator "human" .
```

---

## 4. Permission model

| Operation | Required permission |
|---|---|
| `GET /v2/annotations` — list with `subjectAppId` filter | Read on the subject entity's collection |
| `GET /v2/annotations/find` | Read on at least one collection |
| `GET /v2/annotations/{appId}` | Read on the annotation's subject's collection |
| `POST /v2/annotations` | Write on the subject entity's collection |
| `PUT /v2/annotations/{appId}` | Write on the annotation's subject's collection |
| `DELETE /v2/annotations/{appId}` | Write on the annotation's subject's collection |

Unauthenticated callers on public collections can read but not write.

---

## 5. MCP tools

The Shepard MCP server exposes 11 tools for the annotation surface.
They are available under the `shepard` MCP server and work in any
MCP-capable client (Claude Desktop, Claude Code, compatible notebooks).

| Tool | Description |
|---|---|
| `list_annotations` | List all annotations for a specific entity (by `subjectAppId`) |
| `list_vocabularies` | List all registered vocabularies with their metadata |
| `search_predicates` | Search for predicate terms across all loaded vocabularies |
| `search_values` | Search for value (object) terms for a given predicate |
| `get_annotation` | Retrieve a single annotation by its `appId` |
| `create_annotation` | Create a new annotation with predicate, object, and optional metadata |
| `update_annotation` | Update an existing annotation's fields (merge-patch semantics) |
| `delete_annotation` | Hard-delete an annotation by `appId` |
| `find_annotated` | Find entities that carry a specific predicate–value combination |
| `suggest_annotations`* | Suggest annotations for an entity using AI (requires SEMA-V6-008) |
| `find_similar_annotated`* | Find entities semantically similar via embedding search (requires SEMA-V6-008) |

\* Stub — returns HTTP 501 until the pg_trgm + pgvector SEMA-V6-008 index ships.

**AI auto-detection**: when called from an AI agent, the MCP layer
attaches `sourceMode: ai` and records the model identifier in the
`:Activity` provenance node — no extra parameter needed.

### Example: annotate a data object via MCP

```
create_annotation(
  subjectAppId="019612aa-…",
  subjectKind="DataObject",
  predicateIri="http://purl.org/dc/terms/description",
  objectLiteral="Turbopump vibration anomaly at t=8s"
)
```

### Example: find all AI-generated annotations in a collection

```
list_annotations(
  subjectAppId="019611f0-…",   # the collection's appId
  subjectKind="Collection"
)
```

Then filter the result by `sourceMode == "ai"`.

---

## 6. SPARQL

Annotations are materialized in the n10s knowledge graph and queryable
through the SPARQL proxy.

```
GET  /v2/semantic/{repoAppId}/sparql?query=<URL-encoded SPARQL>
POST /v2/semantic/{repoAppId}/sparql
     Content-Type: application/sparql-query
```

Use `repoAppId` = the `appId` of your SemanticRepository (see
[semantic-repositories reference](/reference/semantic-repositories/)).

### Example: list all annotations for a data object

```sparql
PREFIX dcterms: <http://purl.org/dc/terms/>

SELECT ?predicate ?object WHERE {
  ?annotation a <urn:shepard:SemanticAnnotation> ;
    <urn:shepard:subjectAppId> "019612aa-…" ;
    <urn:shepard:predicateIri> ?predicate ;
    <urn:shepard:objectLiteral> ?object .
}
```

### Example: find all data objects with a specific material annotation

```sparql
PREFIX mat: <https://w3id.org/material-ontology/>

SELECT DISTINCT ?subjectAppId WHERE {
  ?annotation a <urn:shepard:SemanticAnnotation> ;
    <urn:shepard:predicateIri> mat:hasMaterial ;
    <urn:shepard:objectIri> <https://w3id.org/material-ontology/CFRP> ;
    <urn:shepard:subjectAppId> ?subjectAppId .
}
```

### Example: list all AI-generated annotations with confidence < 0.8

```sparql
SELECT ?subjectAppId ?predicateIri ?objectLiteral ?confidence WHERE {
  ?annotation a <urn:shepard:SemanticAnnotation> ;
    <urn:shepard:sourceMode> "ai" ;
    <urn:shepard:confidence> ?confidence ;
    <urn:shepard:subjectAppId> ?subjectAppId ;
    <urn:shepard:predicateIri> ?predicateIri ;
    <urn:shepard:objectLiteral> ?objectLiteral .
  FILTER(?confidence < 0.8)
}
```

---

## 7. Source mode and provenance

Every annotation write records a `sourceMode` that identifies who
(or what) produced the annotation:

| Mode | When to use |
|---|---|
| `human` | Researcher types it in the UI or calls the API manually |
| `ai` | LLM or ML pipeline generates the annotation; set via `X-AI-Agent` header |
| `collaborative` | Human reviews and approves an AI suggestion |

The `sourceMode` is stored on the `:SemanticAnnotation` node **and** on
the associated `:Activity` (provenance event). This satisfies the f(ai)²r
provenance requirement: every AI write is auditable and distinguishable
from human writes.

---

## 8. Validity window

Set `validFromMillis` and `validUntilMillis` (epoch milliseconds) to
express that an assertion was only true during a specific period:

```json
{
  "predicateIri": "http://schema.org/operatingStatus",
  "objectLiteral": "operational",
  "validFromMillis": 1700000000000,
  "validUntilMillis": 1748217600000
}
```

Annotations with `validUntilMillis < now` are returned by the API but
flagged as expired. The UI shows expired annotations with a muted style.
Omit both fields for timeless assertions.

---

## 9. Bootstrap vocabularies

The following vocabularies are pre-seeded at first startup (V72 migration):

| Short name | Prefix | IRI base |
|---|---|---|
| Dublin Core Terms | `dcterms:` | `http://purl.org/dc/terms/` |
| PROV-O | `prov:` | `http://www.w3.org/ns/prov#` |
| schema.org | `schema:` | `https://schema.org/` |
| DataCite | `datacite:` | `http://purl.org/spar/datacite/` |
| CHAMEO | `chameo:` | `https://w3id.org/chameo/chameo#` |
| Material OWL | `mat:` | `https://w3id.org/material-ontology/` |
| metadata4ing | `m4i:` | `https://w3id.org/nfdi4ing/metadata4ing#` |
| SKOS | `skos:` | `http://www.w3.org/2004/02/skos/core#` |
| GeoSPARQL | `geo:` | `http://www.opengis.net/ont/geosparql#` |
| Shepard internal | `shepard:` | `https://shepard.dlr.de/ontology/` |

For managing vocabularies (uploading custom ontologies, enabling/disabling),
see the [manage-vocabularies runbook](/admin/runbooks/manage-vocabularies/) and
[semantic-repositories reference](/reference/semantic-repositories/).

---

## 10. Autocomplete / term search

The annotation dialog and `search_predicates` / `search_values` MCP tools
both use:

```
GET /v2/semantic/terms/search?q=<text>&type=predicate|value
```

Results are drawn from the n10s knowledge graph — only terms from
loaded and enabled vocabularies appear. The minimum query length is
2 characters; results are debounced at 300 ms in the UI.

---

## 10b. Channel unit auto-inference (AI1v)

Every Timeseries channel that lands in `channel_metadata` is auto-tagged
with a QUDT unit IRI when its `field` name carries a deterministic unit
hint. The annotation appears on the channel's `:AnnotatableTimeseries`
bridge node, alongside the 5-tuple channel-identity annotations, with:

| Field                 | Value                                                                  |
| --------------------- | ---------------------------------------------------------------------- |
| `propertyIRI`         | `urn:shepard:unit`                                                     |
| `valueIRI`            | a QUDT canonical IRI, e.g. `http://qudt.org/vocab/unit/MilliM`         |
| `valueName`           | human-readable label, e.g. `millimeter`                                |
| `subjectKind`         | `AnnotatableTimeseries`                                                |
| `subjectAppId`        | the channel's UUID v7 `shepardId`                                      |
| `sourceMode`          | `ai` (rule-based; the operator has not yet confirmed it)               |
| `source`              | `ts-channel-unit-suffix`                                               |
| `confidence`          | `1.0` (SUFFIX) · `0.9` (WELDING_CAP) · `0.85` (PREFIX_HEURISTIC)       |

### Resolution tiers (Phase 1, deterministic)

1. **SUFFIX** — name ends in `_mm`, `_mm_s`, `_um`, `_N`, `_Nm`, `_kN`,
   `_J`, `_K`, `_C`, `_degC`, `_deg`, `_bar`, `_psi`, `_g`, `_Pa`. The
   longest matching suffix wins (so `_mm_s` beats `_mm`).
2. **PREFIX_HEURISTIC** — joint angles `j1_…j7_` → degree, plus
   `acc_`, `rpm_`, `mdot_`, `vib_`, and the rocket-engine LUMEN
   conventions (`tc_`, `pc_`, `p_inj_`, `p_tank_`, `t_coolant_`,
   `t_lox_`, `lch4_temperature`, `turbopump_bearing_temp`,
   `turbopump_vibration`, `strain_`).
3. **WELDING_CAP** — `CM_`, `W1_`, `W2_`, `WC_` cap-controller channels
   disambiguate `_I` → Ampere, `_U` → Volt. The remaining tails (`_p`,
   `_t`) are ambiguous between power/pressure and time/temperature and
   stay un-annotated.
4. **AMBIGUOUS** — no deterministic rule matched. A structured warning
   is logged (`AI1v: container=… → AMBIGUOUS`) and no annotation is
   written. Phase 2 (LLM with parent-DataObject context) is gated on
   the AI plugin (AI1a) and will resolve these.

### Why `sourceMode = "ai"` if no LLM is invoked?

Because the inference is **automated and not human-confirmed**, which is
the property the EU AI Act Article 50 disclosure cares about. When a
user accepts or overrides the annotation via the UI, the `sourceMode`
flips to `collaborative`.

### Idempotency

Existing `ts-channel-unit-suffix` annotations are purged on each channel
re-upsert (mirroring the `ts-channel-metadata` 5-tuple purge), so the
rule table is the single source of truth — operator edits to the
annotation tier configuration take effect on the next ingest.

### QuantityKind derivation

The annotation carries the **unit** IRI only. The matching QUDT
**QuantityKind** (e.g. `qudt:QuantityKind/Length` for `MilliM`) is
derived at presentation time by the M4I-d-3 renderer; we do not denormalise
it onto the annotation.

### Reference impl

`backend/.../semantic/services/ChannelUnitInferenceService.java` — the
Java service. The wire-up to the channel write path is in
`TimeseriesSemanticDualWriteService.dualWriteChannelMetadata(...)`.

The same rule set is also shipped as a standalone Python recovery
script for backfilling existing un-annotated channels:
`examples/mffd-showcase/scripts/recovery/annotate-channel-axes-and-units.py`.

---

## 11. Wire shapes (JSON-LD and Turtle)

The `GET /v2/annotations/{appId}/export/turtle` endpoint returns
[W3C Web Annotation](https://www.w3.org/TR/annotation-model/) Turtle.
The REST API endpoints return JSON. A full JSON-LD context for the
annotation wire shape is served from:

```
GET /v2/annotations/context
```

---

## See also

- [Annotating data (task guide)](/help/annotating-data/)
- [Manage vocabularies (operator runbook)](/admin/runbooks/manage-vocabularies/)
- [Semantic repositories (reference)](/reference/semantic-repositories/)
- [Container annotations (reference)](/reference/container-annotations/)
- [Provenance (reference)](/reference/provenance/)
