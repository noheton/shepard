---
stage: feature-defined
last-stage-change: 2026-05-26
---

# v5 Survey Bundle (v15.10)

Audits the v5 OpenAPI surface (`fixtures/v5/openapi-5.4.0.json`, upstream DLR Shepard
v5.2.0 wire contract — 88 path+method combinations) against what the v15/v16 MFFD
importer (`mffd-import-v15.py`, v16.3) actually calls on the **source** instance
(DLR cube3), and what it calls on the **dest** instance (nuclide, mix of v1 compat +
v2 fork surface).

---

## Coverage map

Legend — `src`: source-side READ from cube3 v5; `dst`: dest-side WRITE to nuclide;
`–`: not called; `v2 eq.`: available v2 equivalent on dest (not necessarily used by v15).

| Endpoint (v5 path+method) | src | dst | v2 eq. on dest | Notes |
|---|---|---|---|---|
| GET /shepard/api/version | – | – | GET /v2/version | Never polled |
| GET /versionz | – | – | – | System endpoint; not checked |
| GET /shepard/api/collections | READ | WRITE | GET /v2/collections | src: list+find by name; dst: existence check |
| POST /shepard/api/collections | – | WRITE | POST /v2/collections | dst: create dest collection |
| GET /shepard/api/collections/{id} | READ | – | GET /v2/collections/{appId} | src: single-collection fetch |
| PUT /shepard/api/collections/{id} | – | – | PATCH /v2/collections/{appId} | Not used; PATCH /shepard/api/ used instead |
| PATCH /shepard/api/collections/{id} | – | WRITE | – | dst: update name/description post-create |
| DELETE /shepard/api/collections/{id} | – | – | – | Never called |
| GET /shepard/api/collections/{id}/dataObjects | READ | WRITE | GET /v2/collections/{appId}/data-objects | src: primary DO enumeration (empty-sentinel pagination); dst: existence check |
| POST /shepard/api/collections/{id}/dataObjects | – | WRITE | POST /v2/collections/{appId}/data-objects | dst: create dest DO |
| GET /shepard/api/dataObjects/{id} | – | – | GET /v2/data-objects/{appId} | Individual DO fetch never called; always bulk via collection list |
| PUT /shepard/api/dataObjects/{id} | – | – | PATCH /v2/data-objects/{appId} | Never used |
| DELETE /shepard/api/dataObjects/{id} | – | – | – | Never called |
| GET /shepard/api/dataObjects/{id}/fileReferences | READ | – | GET /v2/data-objects/{appId}/file-references | src: enumerate file refs per DO |
| POST /shepard/api/dataObjects/{id}/fileReferences | – | WRITE | POST /v2/data-objects/{appId}/file-references | dst: create dest file ref |
| GET /shepard/api/fileReferences/{id} | – | – | GET /v2/file-references/{appId} | Not called directly; enumerated via collection list |
| PUT /shepard/api/fileReferences/{id} | – | – | – | Never called |
| DELETE /shepard/api/fileReferences/{id} | – | – | – | Never called |
| GET /shepard/api/fileReferences/{id}/payload | READ | – | GET /v2/file-references/{appId}/payload | src: bare /payload fallback; primary path uses /payload/{oid} |
| GET /shepard/api/fileReferences/{id}/payload/{oid} | READ | – | – | src: primary file download path; no v2 equivalent yet |
| POST /shepard/api/fileReferences/{id}/payload/{oid} | – | – | – | Never called |
| DELETE /shepard/api/fileReferences/{id}/payload/{oid} | – | – | – | Never called |
| GET /shepard/api/dataObjects/{id}/timeseriesReferences | READ | – | GET /v2/data-objects/{appId}/timeseries-references | src: enumerate TS refs per DO |
| POST /shepard/api/dataObjects/{id}/timeseriesReferences | – | WRITE | POST /v2/data-objects/{appId}/timeseries-references | dst: create dest TS ref |
| GET /shepard/api/timeseriesReferences/{id} | – | – | GET /v2/timeseries-references/{appId} | Not called directly |
| PUT /shepard/api/timeseriesReferences/{id} | – | – | – | Never called |
| DELETE /shepard/api/timeseriesReferences/{id} | – | – | – | Never called |
| GET /shepard/api/timeseriesReferences/{id}/export | READ | – | – | src: ROW csv export; no v2 equivalent |
| POST /shepard/api/dataObjects/{id}/structuredDataReferences | – | WRITE | POST /v2/data-objects/{appId}/structured-data-references | dst: create dest SDR |
| GET /shepard/api/dataObjects/{id}/structuredDataReferences | READ | – | GET /v2/data-objects/{appId}/structured-data-references | src: enumerate SDRs per DO |
| GET /shepard/api/structuredDataReferences/{id} | – | – | – | Not called directly |
| PUT /shepard/api/structuredDataReferences/{id} | – | – | – | Never called |
| DELETE /shepard/api/structuredDataReferences/{id} | – | – | – | Never called |
| GET /shepard/api/structuredDataReferences/{id}/payload | READ | – | – | src: JSON payload fetch |
| POST /shepard/api/structuredDataReferences/{id}/payload | – | WRITE | – | dst: JSON payload upload |
| DELETE /shepard/api/structuredDataReferences/{id}/payload | – | – | – | Never called |
| GET /shepard/api/dataObjects/{id}/dataObjectReferences | – | – | – | **DROPPED** — silently omitted |
| POST /shepard/api/dataObjects/{id}/dataObjectReferences | – | – | – | **DROPPED** — intra-collection DO→DO links not migrated |
| DELETE /shepard/api/dataObjectReferences/{id} | – | – | – | Never called |
| GET /shepard/api/dataObjects/{id}/collectionReferences | – | – | – | **DROPPED** — silently omitted |
| POST /shepard/api/dataObjects/{id}/collectionReferences | – | – | – | **DROPPED** — cross-collection links not migrated |
| DELETE /shepard/api/collectionReferences/{id} | – | – | – | Never called |
| GET /shepard/api/dataObjects/{id}/uriReferences | – | – | – | **DROPPED** — silently omitted |
| POST /shepard/api/dataObjects/{id}/uriReferences | – | – | – | **DROPPED** — external URL links not migrated |
| DELETE /shepard/api/uriReferences/{id} | – | – | – | Never called |
| GET /shepard/api/dataObjects/{id}/spatialDataReferences | – | – | – | **DROPPED** — silently omitted |
| POST /shepard/api/dataObjects/{id}/spatialDataReferences | – | – | – | **DROPPED** — spatial/PostGIS refs not migrated |
| GET /shepard/api/spatialDataReferences/{id} | – | – | – | Never called |
| DELETE /shepard/api/spatialDataReferences/{id} | – | – | – | Never called |
| GET /shepard/api/fileContainers | – | – | GET /v2/file-containers | Never listed; looked up via ref |
| POST /shepard/api/fileContainers | – | – | – | v2 POST /v2/files used instead |
| GET /shepard/api/fileContainers/{id} | – | – | GET /v2/file-containers/{appId} | Never fetched directly |
| PUT /shepard/api/fileContainers/{id} | – | – | – | Never called |
| DELETE /shepard/api/fileContainers/{id} | – | – | – | Never called |
| POST /shepard/api/fileContainers/{id}/payload | – | – | – | v2 presigned URL flow used instead |
| GET /shepard/api/fileContainers/{id}/payload/{oid} | – | – | – | Never called |
| GET /shepard/api/timeseriesContainers | – | – | – | Never listed |
| POST /shepard/api/timeseriesContainers | – | WRITE | – | dst: create dest TS container |
| GET /shepard/api/timeseriesContainers/{id} | READ | – | – | src: fetch container metadata |
| PUT /shepard/api/timeseriesContainers/{id} | – | – | – | Never called |
| DELETE /shepard/api/timeseriesContainers/{id} | – | – | – | Never called |
| GET /shepard/api/timeseriesContainers/{id}/timeseries | READ | – | – | src: list channels (5-tuple descriptors) in container |
| POST /shepard/api/timeseriesContainers/{id}/import | – | WRITE | – | dst: batch CSV channel import |
| GET /shepard/api/structuredDataContainers | – | – | – | Never listed |
| POST /shepard/api/structuredDataContainers | – | WRITE | – | dst: create dest SDC |
| GET /shepard/api/structuredDataContainers/{id} | – | – | – | Never fetched directly |
| PUT /shepard/api/structuredDataContainers/{id} | – | – | – | Never called |
| DELETE /shepard/api/structuredDataContainers/{id} | – | – | – | Never called |
| GET /shepard/api/semanticRepositories | READ | WRITE | GET /v2/semantic/repositories | src: list repos; dst: find/create dest repos |
| POST /shepard/api/semanticRepositories | – | WRITE | POST /v2/semantic/repositories | dst: create dest repo if missing |
| GET /shepard/api/semanticRepositories/{id} | – | – | – | Not fetched directly |
| PUT /shepard/api/semanticRepositories/{id} | – | – | – | Never called |
| DELETE /shepard/api/semanticRepositories/{id} | – | – | – | Never called |
| GET .../semanticAnnotations (dataObject-level) | – | – | GET /v2/data-objects/{appId}/semantic-annotations | **SOURCE ANNOTATIONS NEVER READ** |
| POST .../semanticAnnotations (dataObject-level) | – | WRITE | POST /v2/data-objects/{appId}/semantic-annotations | dst: provenance/f(ai)²r batch writeback only |
| DELETE .../semanticAnnotations/{id} (DO-level) | – | – | – | Never called |
| GET .../semanticAnnotations (collection-level) | – | – | GET /v2/collections/{appId}/semantic-annotations | **SOURCE ANNOTATIONS NEVER READ** |
| POST .../semanticAnnotations (collection-level) | – | WRITE | – | dst: provenance batch writeback only |
| GET .../semanticAnnotations (reference-level) | – | – | – | **SOURCE REF ANNOTATIONS NEVER READ** |
| POST .../semanticAnnotations (reference-level) | – | – | – | **DROPPED** — not written to dest either |
| GET /shepard/api/labJournalEntries | – | – | GET /v2/lab-journal-entries | **SOURCE NARRATIVE NEVER READ** |
| POST /shepard/api/labJournalEntries | – | – | POST /v2/lab-journal-entries | **DROPPED** — not written to dest |
| GET /shepard/api/labJournalEntries/{id} | – | – | – | Never called |
| PUT /shepard/api/labJournalEntries/{id} | – | – | – | Never called |
| DELETE /shepard/api/labJournalEntries/{id} | – | – | – | Never called |
| GET /shepard/api/collections/{id}/permissions | READ | WRITE | GET /v2/collections/{appId}/permissions | src: read source ACL; dst: replicate to dest |
| PUT /shepard/api/collections/{id}/permissions | – | WRITE | PUT /v2/collections/{appId}/permissions | dst: set dest ACL from source |
| GET /shepard/api/users | READ | – | GET /v2/users | src: list users for permission mapping |
| GET /shepard/api/users/{sub} | READ | – | GET /v2/users/{sub} | src: resolve username → userId for ACL |
| POST /shepard/api/users/{username}/apikeys | – | – | POST /v2/users/me/api-keys | dest uses v2 path instead |
| GET /collections/{id}/versions | – | – | – | Absent from v5 fixture (confirmed); 404-tolerant in import_upstream.py |

**Summary**: Of 88 v5 endpoints, v15 reads from **14** source-side endpoints and writes to
**14** dest-side endpoints (overlapping with compat surface + 6 v2-only dest calls).
**60 endpoints** are never called in either direction.

---

## Unused v5 endpoints — data that stays on source

The following categories of data are present on the source cube3 instance but are
**never read** by the v15 importer. This is data loss — it exists on source and is
not present on dest after migration.

### 1. Four silently-dropped reference types

| Reference type | v5 endpoint (GET) | Data lost |
|---|---|---|
| dataObjectReferences | GET /dataObjects/{id}/dataObjectReferences | Intra-collection DO→DO typed links (cross-DO relationships beyond predecessor/parent) |
| collectionReferences | GET /dataObjects/{id}/collectionReferences | Cross-collection foreign-collection pointers from a DO |
| uriReferences | GET /dataObjects/{id}/uriReferences | External URL links (e.g. DOI links, instrument pages, equipment manuals) |
| spatialDataReferences | GET /dataObjects/{id}/spatialDataReferences | Spatial data / PostGIS geometry payloads |

These four reference kinds are never enumerated on source, so any DO in cube3 that
carries such references will arrive in dest with those links absent. This is silent —
no warning is emitted.

### 2. Source semantic annotations — complete loss

v5 exposes semantic annotations at three levels:
- DataObject-level: `GET .../dataObjects/{id}/semanticAnnotations`
- Collection-level: `GET .../collections/{id}/semanticAnnotations`
- Reference-level: `GET .../{kind}References/{id}/semanticAnnotations`

None of these are read from the source. The v15 importer **writes** semantic
annotations to dest (provenance writeback and f(ai)²r predicates), but it does
not migrate the **source's own** annotations. Any CHAMEO, Dublin Core, QUDT, or
custom RDF triples that researchers on cube3 attached to their DOs are not present
in the dest instance.

### 3. Lab journal entries — narrative loss

`GET /shepard/api/labJournalEntries` and the collection-scoped listing are never
called. Any free-text notes, procedure narratives, or Markdown lab journal pages
created by cube3 users are silently absent in dest.

### 4. Source DataObject fields consumed selectively

The v5 `DataObject` schema has 14 fields. The v15 importer consumes 8 and drops 6:

| Field | Consumed? | Notes |
|---|---|---|
| id | YES | Used as source-side key; mapped to `sourceId` attribute on dest |
| name | YES | Mapped directly |
| description | YES | Mapped directly |
| attributes | YES | Mapped directly (all key-value pairs) |
| parentId | YES | PRESERVE_HIERARCHY replication uses this |
| predecessorIds | YES | Ancestor chain replicated |
| creationDate / createdAt | YES | Wire-shape dual-probe (live cube3 uses `creationDate`, spec says `createdAt`) |
| modificationDate / updatedAt | YES | Same dual-probe |
| createdBy | **NO** | Original author identity dropped |
| updatedBy | **NO** | Last-editor identity dropped |
| collectionId | **NO** | Redundant (known from context); safe to drop |
| successorIds | **NO** | Not needed — predecessor chain is sufficient for DAG reconstruction |
| childrenIds | **NO** | Not needed — parentId on children reconstructs the tree |
| incomingIds | **NO** | Not needed — predecessorIds is the canonical direction |
| referenceIds | **NO** | These are DataObjectReference node IDs, NOT ref payloads — safe to drop |

### 5. Source TimeseriesReference metadata not migrated

The v5 `TimeseriesReference` has 11 fields. The importer only uses `id, name,
timeseriesContainerId` — the three wiring fields. The following are ignored on source:

| Field | Value lost |
|---|---|
| start | Declared time window start on source |
| end | Declared time window end on source |
| timeseries[] | The 5-tuple channel descriptor array: `{measurement, device, location, symbolicName, field}` — these are the channel identity anchors registered on source |

The importer re-derives channel descriptors from the timeseries container directly
(`GET .../timeseriesContainers/{id}/timeseries`), so functional TS data survives.
But the reference-level `start/end` window boundaries and any per-reference
channel subset are lost.

---

## Field gaps — v5 fields not yet in v2 (enrichment candidates)

These fields exist in the v5 wire contract but have no direct v2 counterpart.
They represent enrichment the importer cannot write to dest even if it reads them.

| v5 field | Entity | v2 status | Notes |
|---|---|---|---|
| `createdBy` / `updatedBy` | DataObject, Collection | Absent from v2 shapes | Original author attribution lost on migration; v2 exposes `createdAt/updatedAt` timestamps but not identity |
| `start` / `end` on TimeseriesReference | TimeseriesReference | Absent from v2 TS-ref shape | Time-window boundary metadata exists in v5 but v2 TS refs carry no declared window |
| `type` on FileReference | FileReference | v2 has no `type` field on file-ref | Content-type hint on reference lost |
| `dataObjectId` on references | All reference kinds | v2 has reverse-link via DO, not on ref | No symmetrical back-pointer on v2 ref objects |
| `structuredDataOids` on StructuredDataReference | StructuredDataReference | v2 SDR carries no `oids` list | Object-ID index for multi-payload refs absent from v2 |
| `timeseries[]` channel array on TimeseriesReference | TimeseriesReference | v2 TS-ref carries no embedded channel descriptor | Must be fetched separately from container; 5-tuple identity lives in container, not in ref |

**v2-only enrichments absent from v5** (dest has these; source cannot supply them):
- `appId` (UUID v7) — stable fork-native identifier; v5 only has numeric Neo4j long IDs
- `properties` — typed ontology-backed property bag; absent from v5
- `snapshots` — immutable checkpoint mechanism; v5 has no equivalent
- `channel_metadata` — the `channel_metadata` table in TimescaleDB that backs `TS-CORE-SCHEMA-01` (shipped 2026-05-26)
- Provenance / f(ai)²r predicates — V61 vocabulary predicates registered in dest

---

## Recommendations

### R1 — Add source semantic annotation read pass (CRITICAL before real-data import)

**Gap**: All domain annotations (CHAMEO instrument metadata, QUDT units, custom RDF triples,
Dublin Core terms) attached to DOs, collections, and references on cube3 are never read.
After migration, researchers see their data but not its semantic context.

**Fix**: In the v15 importer, add a post-creation pass:
```
for each migrated DO:
    annotations = GET /shepard/api/dataObjects/{sourceId}/semanticAnnotations
    if annotations:
        POST /v2/data-objects/{destAppId}/semantic-annotations  (for each predicate)
```
Same pattern for collection-level and reference-level annotations. The v61 shepard:
vocabulary already registers provenance predicates; this pass adds domain-semantics
predicates sourced from cube3.

**Priority**: Highest. This is the only category of data loss that cannot be reconstructed
post-migration without re-accessing the source instance.

### R2 — Add lab journal migration pass (HIGH before narrative content goes stale)

**Gap**: Lab journal entries are the free-text narrative layer of research. Any procedural
notes, anomaly descriptions, shift logs, or protocol records created by cube3 users
exist only on source after migration.

**Fix**: `GET /shepard/api/collections/{id}/labJournalEntries` (or enumerate via DO-scoped
endpoint if v5 exposes it), then `POST /v2/lab-journal/entries` on dest per entry.
The v2 lab-journal endpoint is already present on dest (`/v2/lab-journal`).

**Priority**: High. Narrative content degrades in value as researcher memory fades.
Lab journals are not reconstructable from sensor data alone.

### R3 — Enumerate and log the four dropped reference types before import (MEDIUM)

**Gap**: dataObjectReferences, collectionReferences, uriReferences, and spatialDataReferences
are never fetched. If cube3 DOs carry these, they are silently absent on dest with no
audit trail of the loss.

**Fix**: Before the main migration loop, add a preflight probe:
```
for each source DO:
    for kind in [dataObjectReferences, collectionReferences, uriReferences, spatialDataReferences]:
        count = len(GET /dataObjects/{id}/{kind})
        if count > 0:
            log WARNING: "DO {id} has {count} {kind} — not migrated"
```
This surfaces the scope of loss without requiring implementation of the full migration.
URI references are the most likely to exist (DOI links, instrument pages); spatial
references are the least likely in the MFFD dataset.

Once scoped, migrate uriReferences first — they are JSON `{uri, name}` pairs, trivially
portable to v2 (could be stored as `attributes["doi"]` or a future `uriReferences` v2
endpoint). DataObject- and collection-references require a v2 endpoint design first.

---

*Generated by agent task #185 (v15.10 v5 survey bundle) — 2026-05-26*
*Source: `fixtures/v5/openapi-5.4.0.json`, `mffd-import-v15.py` v16.3, `import_upstream.py`*
