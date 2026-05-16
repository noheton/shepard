# aidocs/77 — Databus + MOSS federation layer for shepard

**Date:** 2026-05-16
**Status:** Design — ready for slice planning.
**Audience:** Contributors; DLR-IT/SISTEC; operators planning a DLR-internal federation stack.
**Purpose:** Specify how a DLR-internal Databus + MOSS instance becomes the federation
layer for shepard deployments across DLR institutes.

Upstream reference: https://github.com/dbpedia/databus-moss-server
Main MOSS repo: https://github.com/dbpedia/databus-moss

Couples to: R1 (Databus publication), aidocs/58 REF1 (Databus rich references),
aidocs/67 (Unhide/HKG publish), aidocs/66 (KIP DOI minting).

---

## 1. What Databus + MOSS is

### 1.1 Databus

DBpedia Databus is an **open data publication and cataloguing platform**. It assigns
stable, dereferenceable URIs to datasets, tracks versions, and exposes a SPARQL endpoint
over the catalogue. A Databus deployment can be self-hosted (DLR-internal) or public.

```
https://databus.example.dlr.de/{publisher}/{group}/{artifact}/{version}
```

Databus handles: persistent identifiers, checksums, download locations, license metadata,
and version history. It does not handle domain-specific metadata about *how* data was
produced.

### 1.2 MOSS (Metadata Overlay Search System)

MOSS extends Databus resources with domain-specific RDF metadata. It is a separate
service that sits alongside Databus and provides:

- **Module system**: each domain of knowledge defines its own module (metadata schema +
  SHACL validation + JSON-LD context + SPARQL indexer). Multiple modules can annotate
  the same Databus resource.
- **Entry API**: `POST /api/v1/save-entry?module=<id>&resource=<databusURI>` receives
  RDF/Turtle, validates it against the module's SHACL shapes, stores header + content
  documents in a graph store (Gstore), and triggers re-indexing via Virtuoso SPARQL.
- **SPARQL surface**: `GET /sparql` proxies to Virtuoso — all MOSS entries across all
  modules are queryable in one place.
- **Graph retrieval**: `GET /g/{path}` returns individual RDF graphs in any supported format.

### 1.3 The architecture in one diagram

```
Institute A                 Institute B
┌────────────┐              ┌────────────┐
│  shepard   │              │  shepard   │
│  (BT)      │              │  (FA)      │
└─────┬──────┘              └─────┬──────┘
      │ publish                   │ publish
      │ Collection as             │ Collection as
      │ Databus artifact          │ Databus artifact
      ▼                           ▼
┌─────────────────────────────────────────────┐
│      DLR-internal Databus instance          │
│  (stable URIs, checksums, download locs)   │
└───────────────────┬─────────────────────────┘
                    │ annotate via MOSS module
                    ▼
┌─────────────────────────────────────────────┐
│       MOSS instance (alongside Databus)     │
│  module: shepard   module: keyword  module: │
│  (PROV-O +         (free tags)      ...     │
│   metadata4ing)                             │
│  SPARQL endpoint across all modules         │
│  → DLR-wide dataset discovery               │
└─────────────────────────────────────────────┘
```

---

## 2. What a shepard MOSS module looks like

MOSS modules are defined by four files in a config directory:

```
config/modules/shepard/
├── module.yml        ← id, label, description, language
├── shapes.ttl        ← SHACL constraints for validation
├── context.jsonld    ← JSON-LD context for the module vocabulary
└── indexer.yml       ← SPARQL queries for Virtuoso indexing
```

### 2.1 `module.yml`

```yaml
id: shepard
label: shepard Research Data Management
description: >
  Provenance and domain metadata for datasets published via the shepard
  active research data management platform. Uses PROV-O and metadata4ing.
language: text/turtle
```

### 2.2 `shapes.ttl` (SHACL validation)

```turtle
@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix prov: <http://www.w3.org/ns/prov#> .
@prefix m4i: <http://w3id.org/nfdi4ing/metadata4ing#> .
@prefix schema: <https://schema.org/> .
@prefix dct: <http://purl.org/dc/terms/> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

:ShepardEntryShape a sh:NodeShape ;
    sh:targetSubjectsOf prov:wasGeneratedBy ;
    sh:property [
        sh:path dct:title ;
        sh:datatype xsd:string ;
        sh:minCount 1 ;
        sh:message "A shepard entry must carry dct:title (Collection name)." ;
    ] ;
    sh:property [
        sh:path prov:wasGeneratedBy ;
        sh:class prov:Activity ;
    ] ;
    sh:property [
        sh:path schema:identifier ;
        sh:datatype xsd:anyURI ;
        sh:description "The shepard Collection appId URI (resolver URL)." ;
    ] .

:ShepardActivityShape a sh:NodeShape ;
    sh:targetClass prov:Activity ;
    sh:property [
        sh:path m4i:ProcessingStep ;
        sh:datatype xsd:string ;
    ] ;
    sh:property [
        sh:path prov:startedAtTime ;
        sh:datatype xsd:dateTime ;
    ] .
```

### 2.3 `indexer.yml`

```yaml
version: "1.0"
indexMode: INDEX_SPARQL_ENDPOINT
sparqlEndpoint: http://virtuoso:8890/sparql
indexFields:
  - fieldName: title
    documentVariable: entry
    query: >
      SELECT DISTINCT ?entry ?title WHERE {
        GRAPH ?g {
          ?entry a <http://dataid.dbpedia.org/ns/moss#MetadataEntry> .
          ?entry <http://dataid.dbpedia.org/ns/moss#extends> ?resource .
        }
        GRAPH ?content {
          ?resource <http://purl.org/dc/terms/title> ?title .
        }
        #VALUES#
      }
  - fieldName: method
    documentVariable: entry
    query: >
      SELECT DISTINCT ?entry ?method WHERE {
        GRAPH ?g {
          ?entry a <http://dataid.dbpedia.org/ns/moss#MetadataEntry> .
          ?entry <http://dataid.dbpedia.org/ns/moss#extends> ?resource .
        }
        GRAPH ?content {
          ?resource <http://www.w3.org/ns/prov#wasGeneratedBy> ?activity .
          ?activity <http://w3id.org/nfdi4ing/metadata4ing#ProcessingStep> ?method .
        }
        #VALUES#
      }
  - fieldName: institute
    documentVariable: entry
    query: >
      SELECT DISTINCT ?entry ?institute WHERE {
        GRAPH ?g {
          ?entry a <http://dataid.dbpedia.org/ns/moss#MetadataEntry> .
          ?entry <http://dataid.dbpedia.org/ns/moss#extends> ?resource .
        }
        GRAPH ?content {
          ?resource <https://schema.org/sourceOrganization> ?institute .
        }
        #VALUES#
      }
```

### 2.4 `context.jsonld`

```json
{
  "@context": {
    "prov": "http://www.w3.org/ns/prov#",
    "m4i": "http://w3id.org/nfdi4ing/metadata4ing#",
    "dct": "http://purl.org/dc/terms/",
    "schema": "https://schema.org/",
    "xsd": "http://www.w3.org/2001/XMLSchema#",
    "title": "dct:title",
    "description": "dct:description",
    "identifier": { "@id": "schema:identifier", "@type": "@id" },
    "wasGeneratedBy": { "@id": "prov:wasGeneratedBy", "@type": "@id" },
    "processingStep": "m4i:ProcessingStep",
    "method": "m4i:Method",
    "institute": "schema:sourceOrganization"
  }
}
```

---

## 3. The federation flow from shepard's side

When a researcher completes a Collection and clicks "Publish", the following pipeline runs
(assuming the operator has configured Databus + MOSS alongside the existing Unhide/KIP path):

```
Collection complete
      │
      ├─ [existing] "Mint KIP PID" → shepard KIP minter
      │   ↳ resolver URL: https://shepard.dlr.de/v2/.../collections/{appId}
      │
      ├─ [existing] "Submit to Unhide" → Helmholtz KG harvest feed
      │   ↳ schema.org JSON-LD; Helmholtz-wide discoverability
      │
      ├─ [existing] "Submit to InvenioRDM" → shepard-plugin-invenio (aidocs/72)
      │   ↳ canonical citable DOI; downloadable RO-Crate
      │
      └─ [NEW] "Publish to DLR Databus" → shepard-plugin-databus
          ├─ 1. Create/update Databus artifact at
          │       databus.dlr.de/{institute}/{group}/{collectionAppId}/{version}
          │   Payload: signed Databus dataid.ttl with download location (presigned S3 URL)
          │
          ├─ 2. POST MOSS entry to shepard module:
          │       POST /api/v1/save-entry?module=shepard
          │              &resource=databus.dlr.de/.../{collectionAppId}/{version}
          │   Body: PROV-O + metadata4ing Turtle (generated from the Collection's
          │          provenance graph + semantic annotations)
          │
          └─ 3. Store :DatabusPublication Neo4j node with Databus artifact URI
```

The SPARQL endpoint at the DLR MOSS instance then answers queries like:
```sparql
SELECT ?dataset ?title ?method ?institute WHERE {
  ?entry a moss:MetadataEntry ;
         moss:module <https://moss.dlr.de/modules/shepard> ;
         moss:extends ?dataset .
  GRAPH ?content {
    ?dataset dct:title ?title ;
             prov:wasGeneratedBy/m4i:ProcessingStep ?method ;
             schema:sourceOrganization ?institute .
  }
}
```
This is DLR-wide cross-institute dataset discovery — no single-point-of-authority required.

---

## 4. Plugin design: `shepard-plugin-databus`

Follows the same `PluginManifest` SPI seam as `shepard-plugin-unhide` and
`shepard-plugin-invenio`. Plugin id: `databus`.

### 4.1 `:DatabusConfig` admin entity

```cypher
(:DatabusConfig {
  appId: "databus-config",
  databusBaseUrl: "https://databus.dlr.de",
  mossBaseUrl: "https://moss.dlr.de",
  publisherName: "dlr-bt",
  group: "shepard-collections",
  mossModule: "shepard",
  apiToken: "<encrypted>",            // used for both Databus and MOSS
  oidcEnabled: true,
  enabled: false
})
```

### 4.2 Admin REST surface

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/v2/admin/databus/config` | Current config (token masked) |
| `PATCH` | `/v2/admin/databus/config` | RFC 7396 merge-patch, instance-admin |
| `POST` | `/v2/admin/databus/config/rotate-token` | Rotate API token |
| `GET` | `/v2/admin/databus/health` | Ping Databus + MOSS; report reachable/unreachable |

CLI parity: `shepard-admin databus {status,enable,disable,set-base-url,set-moss-url,set-publisher,health}`

### 4.3 Researcher-facing REST surface

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/v2/collections/{appId}/publish-to-databus` | Trigger publication |
| `GET` | `/v2/databus/publications/{pubId}` | Poll publication status |
| `GET` | `/v2/collections/{appId}/databus-publications` | List all publications |

### 4.4 PROV-O + metadata4ing Turtle generation

The plugin generates the Turtle body for the MOSS entry by walking the Collection's
provenance graph (already populated by PROV1a):

```turtle
<{databusArtifactUri}> a prov:Entity ;
    dct:title "{Collection.name}" ;
    dct:description "{Collection.description}" ;
    schema:identifier <{kipPidResolverUrl}> ;
    schema:sourceOrganization "{instanceConfig.organizationName}" ;
    schema:creator "{User.orcid or User.displayName}" ;
    prov:wasGeneratedBy [
        a prov:Activity ;
        m4i:ProcessingStep "{annotation.m4i:Method}" ;
        prov:startedAtTime "{Collection.createdAt}" ;
        prov:endedAtTime "{Collection.updatedAt}" ;
    ] .
```

Semantic annotations on the Collection (from PROV1a / N1) are mapped to `m4i:*` predicates
in the Turtle. The full PROV-O graph is included when the Collection has predecessor
edges (derived-from chain).

### 4.5 `:DatabusPublication` Neo4j model

```
(:Collection {appId: $collectionAppId})
    -[:HAS_DATABUS_PUBLICATION]->
(:DatabusPublication {
    appId: $pubId,                  // UUID v7
    databusArtifactUri: "...",
    databusVersionUri: "...",
    mossEntryUri: "...",
    status: "PUBLISHED",            // PENDING | PUBLISHED | FAILED
    publishedAt: <ISO8601>,
    publishedBy: <userAppId>
})
```

---

## 5. Deployment: self-hosted DLR Databus + MOSS

The full stack for a DLR-internal deployment:

```yaml
# docker-compose excerpt (adds to aidocs/74's DLR-BT pilot stack)
services:
  databus:
    image: dbpedia/databus:latest          # DLR-internal Databus
    environment:
      - DATABUS_RESOURCE_BASE_URL=https://databus.dlr.de
    volumes:
      - databus-data:/data
    ports:
      - "3000:3000"

  moss-server:
    image: dbpedia/databus-moss-server:dev
    environment:
      - MOSS_BASE_URL=https://moss.dlr.de
      - GSTORE_BASE_URL=http://gstore:5003
      - STORE_SPARQL_ENDPOINT=http://virtuoso:8890/sparql
      - AUTH_OIDC_ISSUER=https://keycloak.dlr.de/realms/dlr
      - AUTH_OIDC_CLIENT_ID=moss
      - CONFIG_PATH=/config
    volumes:
      - ./moss-config:/config              # contains modules/shepard/ etc.

  moss-frontend:
    image: dbpedia/databus-moss-frontend:dev
    ports:
      - "5000:5000"

  gstore:
    image: dbpedia/gstore:dev
    ports:
      - "5003:5003"

  virtuoso:
    image: openlink/virtuoso-opensource-7:latest
    ports:
      - "8890:8890"
```

The shepard MOSS module config (`modules/shepard/`) is maintained in a DLR git repository
and mounted as a volume — the operator updates the SHACL shapes and indexer by pushing to
git and restarting MOSS.

---

## 6. Relationship to existing federation channels

| Channel | Direction | Audience | Metadata format | When to use |
|---|---|---|---|---|
| **Unhide/HKG** (aidocs/67) | push | Helmholtz-wide | schema.org + metadata4ing JSON-LD | Helmholtz KG search; compliance |
| **InvenioRDM** (aidocs/72) | push | Institutional / public | DC + RO-Crate | Citable archival record; DOI |
| **KIP DOI** (aidocs/66) | push | Machine-readable | KIP JSON-LD | Workbench-side PID; resolver |
| **Databus + MOSS** (this doc) | push+query | DLR-internal | PROV-O + metadata4ing Turtle | DLR-wide cross-institute SPARQL; bidirectional annotation |

Databus+MOSS is the **DLR-internal federation layer**; Unhide is the **Helmholtz-external
discovery layer**. They are complementary, not competing:
- MOSS entries can include the KIP PID as `schema:identifier` → navigates to the shepard
  workbench page
- MOSS entries can include the InvenioRDM DOI → navigates to the citable archival record
- MOSS entries can include the Unhide feed URL → cross-links the two discovery systems

### 6.1 R1 (Databus reference in dispatcher) update

The existing `R1` item in the dispatcher ("Databus integration for referencing foreign
systems — wait for Databus API spec stabilisation") is now superseded by this design, which
uses the Databus **publication API** (well-stabilised) rather than just referencing. The
`R1` item can be re-scoped to: "implement `shepard-plugin-databus` per `aidocs/77`."

### 6.2 REF1 (DBpedia Databus rich references — aidocs/58)

REF1 is about **consuming** Databus resources as rich references within shepard (fetching
preview metadata from Databus to populate a `DataObjectReference`). This is orthogonal
to the federation publication path: REF1 lets researchers link their DataObjects to
external Databus datasets; this doc lets shepard publish its own datasets to Databus.
Both should ship.

---

## 7. Federation semantics and the DLR MOSS module

### 7.1 What the MOSS module is NOT

- Not a replacement for shepard's internal provenance graph — the full PROV-O graph stays
  in Neo4j; the MOSS entry is a curated **summary projection** for federation.
- Not a synchronisation point — MOSS entries are published on researcher action
  ("Publish to Databus"), not on every shepard write.
- Not access control — the DLR MOSS instance is internal; access is controlled by DLR's
  Keycloak (Helmholtz AAI). The MOSS entry visibility follows the Databus resource's
  access policy.

### 7.2 Updating an existing entry

When a researcher republishes a Collection (e.g., after adding more data), the plugin:
1. Creates a new Databus **version** (Databus versioning is append-only; old versions are
   preserved).
2. **Overwrites** the MOSS entry for the new version (MOSS `save-entry` is idempotent on
   the same resource URI).
3. The previous version's MOSS entry is preserved (linked to the old Databus version URI).

### 7.3 Multi-institute SPARQL federation

With multiple shepard instances publishing to the same DLR Databus+MOSS, the MOSS SPARQL
endpoint becomes the single cross-institute query surface. Example:

```sparql
PREFIX m4i: <http://w3id.org/nfdi4ing/metadata4ing#>
PREFIX prov: <http://www.w3.org/ns/prov#>
PREFIX schema: <https://schema.org/>

SELECT ?dataset ?institute ?method WHERE {
  ?entry <http://dataid.dbpedia.org/ns/moss#module>
         <https://moss.dlr.de/modules/shepard> ;
         <http://dataid.dbpedia.org/ns/moss#extends> ?dataset .
  GRAPH ?content {
    ?dataset schema:sourceOrganization ?institute ;
             prov:wasGeneratedBy/m4i:ProcessingStep ?method .
    FILTER(?method = "drop-test")
  }
}
```

This query answers: "which DLR institutes have published drop-test data via shepard?"
without requiring any of the institute shepard instances to be directly accessible.

---

## 8. Phasing

| Phase | ID | Deliverable | Gate |
|---|---|---|---|
| 1 | MB1a | Plugin skeleton: `PluginManifest`, `:DatabusConfig`, admin GET/PATCH/health, CLI `status/enable/disable` | PM1a (plugin SPI) |
| 2 | MB1b | PROV-O + metadata4ing Turtle generator from Collection provenance graph | PROV1a (provenance graph) |
| 3 | MB1c | MOSS entry publication: `POST /api/v1/save-entry` with the generated Turtle; `:DatabusPublication` node | MB1b |
| 4 | MB1d | Databus artifact creation/update (dataid.ttl generation + Databus API call); full publication flow | MB1c, FS1g (presigned URL for download location) |
| 5 | MB1e | Researcher REST surface: `POST /v2/collections/{appId}/publish-to-databus`; async job + polling | MB1d, aidocs/32 job pattern |
| 6 | MB1f | Notification on publish/fail (N10a SSE + N10b email) | N10a/b (aidocs/72 §10) |
| 7 | MB1g | MOSS SPARQL proxy: `GET /v2/federation/sparql` proxies the DLR MOSS SPARQL endpoint with shepard auth; allows researchers to query the DLR-wide dataset index from within the shepard UI | MB1c |

**MB1c is the minimal viable integration.** MB1d+MB1e complete the production path.
MB1g is the researcher-facing discovery payoff.

The DLR-internal Databus+MOSS deployment can be provided as a `docker-compose` profile
(`databus` profile, analogous to the `hdf`/`spatial` profiles).

---

## 9. Operator setup

1. Deploy the DLR Databus+MOSS stack (see §5; `docker compose --profile databus up`).
2. Add the `modules/shepard/` module config (§2) to the MOSS `CONFIG_PATH`.
3. In shepard admin:
   ```
   shepard-admin databus set-base-url https://databus.dlr.de
   shepard-admin databus set-moss-url https://moss.dlr.de
   shepard-admin databus set-publisher dlr-bt
   shepard-admin databus set-api-token <token>
   shepard-admin databus enable
   ```
4. Verify connectivity: `shepard-admin databus health`

For multi-institute deployment, each institute's shepard instance points to the **same**
Databus+MOSS instance. The `publisherName` distinguishes institute namespaces in the
Databus URI hierarchy.

---

## 10. See also

- `aidocs/67-unhide-publish-plugin.md` — Helmholtz KG channel (complementary)
- `aidocs/72-invenio-publishing-plugin.md` — InvenioRDM archival channel (complementary)
- `aidocs/66-hmc-kip-integration.md` — KIP DOI minting (links into MOSS entries)
- `aidocs/58-ui-and-graph-ergonomics.md §REF1` — Databus rich references (consuming side)
- `aidocs/55-provenance-and-activity-overhaul.md` — PROV-O graph that MOSS entries summarise
- `aidocs/47-dev-experience-and-plugin-system.md` — plugin SPI that MB1a follows
- https://github.com/dbpedia/databus-moss-server — MOSS server source
- https://github.com/dbpedia/databus-moss — MOSS main repo (frontend + deployment)
