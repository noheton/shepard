# API Scrutinizer — v14 → v15 import-script review

Source: `examples/mffd-showcase/scripts/mffd-dropbox-import.py` (v14)
Spec: DLR Shepard v5.4.0 OpenAPI (`/root/.claude/uploads/33f9b6cd-…/605810e8-openapi_1.json`)
Destination: fork (byte-compat `/shepard/api/*` + additive `/v2/*`)

The source side (DLR) is **strictly v5.4.0**: no `appId` on Collection/DataObject, no `/users/currentUser`, no `/v2/*`, `predecessorIds[]` writable inside the DataObject body (no `/predecessors/{predId}` path), `csv_format ∈ {ROW, COLUMN}`. The destination side (fork) inherits everything plus v2 additions.

## Bugs in v14 (every wire-shape mismatch)

| # | Line | Bug | OpenAPI evidence | Sev |
| - | ---: | --- | ---- | --- |
| A | 597 | `link_ts_to_do` POSTs `{name, timeseriesContainerId}` — missing required `timeseries[]` (minItems:1). | `TimeseriesReference.required: [...,"timeseries",...]`; `timeseries: minItems:1`. | CRITICAL |
| B | 640–641 | `upload_structured_payload` uses **PUT** on `/structuredDataContainers/{id}/payload`. The path accepts **GET + POST only** (POST is the upload). | `paths."/structuredDataContainers/{id}/payload": {get, post}` (no put). | CRITICAL |
| C | 635 | `link_structured_to_do` POSTs `{name, structuredDataContainerId}` — missing required `structuredDataOids[]` (minItems:1). | `StructuredDataReference.required: [...,"structuredDataOids",...]`; `structuredDataOids: minItems:1`. | CRITICAL |
| D | 641 | Even with PUT→POST fixed, the body shape is wrong. POST expects `StructuredDataPayload = {structuredData:{name}, payload:string}` where **payload is a STRING** (minLength:2). v14 sends the raw object/array as `json=payload`. | `components.schemas.StructuredDataPayload = {structuredData, payload:{type:string,minLength:2}}`. | CRITICAL |
| E | 339–351 | `download_structured` does `r.json()` and returns it. GET `/structuredDataReferences/{id}/payload` returns `StructuredDataPayload[]` — an **array of wrappers** each carrying `{structuredData, payload}` where `payload` is the serialized string. v14 stores the wrapper, then re-uploads it as a raw object → double corruption. | `responses.200.schema = array of StructuredDataPayload`. | CRITICAL |
| F | 287–294, 318–323 | `_fetch_file_refs` / `_fetch_structured_refs` ignore `fileOids[]` and `structuredDataOids[]`. They iterate only the ref node, not its OIDs. Multi-OID references → only ref-node metadata copied; payloads silently dropped. | `FileReference.fileOids:[string],minItems:1`; `StructuredDataReference.structuredDataOids:[string],minItems:1`. | CRITICAL |
| G | 1262–1290 | Order bug: `link_ts_to_do` (creates `TimeseriesReference`) is called **before** `import_ts_csv` (populates the container). At link time the container has **zero `Timeseries` entries**, so even with a correctly-shaped body the POST still fails `minItems:1`. Bug A and bug G compound. | Same as A. | CRITICAL |
| H | 332 | `export_ts` sends `csv_format=WIDE`. The spec enum is `{ROW, COLUMN}` only (default ROW). Source returns 400 / falls back silently. | `CsvFormat.enum = ["ROW","COLUMN"]`. | MAJOR |
| I | 479–488 | `_link_predecessor` PUTs `/collections/{c}/dataObjects/{d}/predecessors/{predId}` — **this path does not exist** in v5.4.0. Every "predecessor link FAILED" warning in the log is this. The DataObject schema has writable `predecessorIds[]`; the correct shape is to set them at POST creation (line 471 already accepts `predecessorIds:[…]`) or via PUT on the DataObject body. | No `/predecessors/{predId}` path; `DataObject.predecessorIds: array<int64>` writable (not readOnly). | CRITICAL |
| J | 555–578 | v1 file-upload fallback POSTs **multipart** to `/dataObjects/{do}/fileReferences`. That endpoint is JSON-only and expects a `FileReference` body. Correct v1 flow is two-step: (1) multipart `POST /fileContainers/{fileContainerId}/payload` → oid; (2) JSON `POST /dataObjects/{do}/fileReferences` with `{name, fileOids:[oid], fileContainerId}`. | `POST .../fileReferences.requestBody.content."application/json": {schema: FileReference}`; multipart only at `POST /fileContainers/{id}/payload`. | MAJOR |
| K | 222 | Warmup fallback hits `/shepard/api/users/currentUser` — no such path exists. v5.4.0 exposes `/users` (list) and `/users/{username}` (specific). Always 404s against a real upstream; only the fork's `/v2/users/me` works. | `paths: /users, /users/{username}, /users/{username}/apikeys, /search/users` only. | MINOR |
| L | 583–589 | `create_ts_container` POSTs `{name, type:"TIMESERIES"}`. `TimeseriesContainer.type` is **readOnly** in the spec — sending it is at best ignored, at worst rejected on strict validators. Same applies to `create_structured_container` (line 624: just `{name}` — fine). Type is set server-side from the container path. | `TimeseriesContainer.properties.type.readOnly: true`. | MINOR |
| M | 401–407 | `find_collection` calls `GET /collections?name=…` then **linearly re-filters** for exact name match. The server already filters by `name` query param. Redundant but harmless. | `GET /collections.parameters.name` (already a filter). | MINOR |
| N | 446–456 | Same redundant client-side filter as M, for `find_data_object`. | `GET .../dataObjects.parameters.name`. | MINOR |
| O | 700–705 | `probe_structured` POSTs `{name, data:{…}}` to `/structuredDataReferences` — body has no `structuredDataContainerId` and no `structuredDataOids[]`. Guaranteed 400. (The log explicitly shows `~ structured data — http 400 (Constraint Violation)` proving the same root cause as bug C.) | Same as C. | MAJOR |
| P | 717–739 | `probe_timeseries` POSTs `{name, measurement, device, location, symbolicName, field}` — that's a `Timeseries` object **flattened into the reference**, not the actual `TimeseriesReference` shape. Also missing `start`, `end`, `timeseries[]`, `timeseriesContainerId`. Guaranteed 400 (the log confirms: `expected without TS container`). The probe is misleading — it implies "fixable with a container" but the body is wrong-shape regardless. | `TimeseriesReference.required` includes `start, end, timeseries, timeseriesContainerId`; `Timeseries` is a sub-object inside `timeseries[]`. | MAJOR |
| Q | 333–337 | `export_ts` returns `r.content` after `r.ok`. If the source returns 200 with an empty body (TS container has no data) the next stage imports an empty CSV — silent corruption. No length / sanity check. | `GET .../export.responses.200: application/octet-stream` (no minLength). | MINOR |
| R | 217–222 | Warmup tries `/v2/users/me` first then falls back to `/shepard/api/users/currentUser`. Against the source (DLR v5.4.0) both 404. Against the dest (fork) the v2 path works. The cross-instance script needs **two** warmups (one against source-client, one against dest-client) — currently it only warmups the dest. Source connectivity is implicitly checked only when `iter_data_objects` first calls. | (architectural, see logs) | MAJOR |
| S | 416–426 | `get_collection_app_id` / `get_collection_name` re-GET the collection on every call. No caching. Combined with the 2× call pattern (`find_collection` already returned the body, then we re-GET for appId) → 2N requests where N suffices. Minor perf issue at scale. | n/a | MINOR |
| T | 600–618 | `import_ts_csv` uses MIME `text/csv` for the multipart part. The spec's `MultipartBodyFileUpload1` has no MIME constraint, but the backend's CSV importer wants the standard `text/csv` — verify; some Quarkus multipart parsers cache the part as `application/octet-stream` unless explicitly told. Risk, not a confirmed bug. | (verify against backend `TimeseriesContainerImporter`). | MINOR |

## v15 new-endpoint design — PROV-O writeback

### Honest answer: there is no Turtle/N-Triples ingest endpoint on the fork's public API surface

Candidates investigated:

| Endpoint | Verdict | Why not |
| --- | --- | --- |
| `POST /v2/admin/semantic/ontologies` (`SemanticAdminRest.uploadOntology`) | NO | Admin-only (`guardAdmin`); treats payload as a **persistent ontology bundle** (SHA-256 + on-disk write + `:UserOntologyBundle` catalogue row); 10 MB cap; refresh-loop runs on every startup. Wrong shape for per-import provenance fragments. |
| `POST /v2/semantic/{repoAppId}/sparql` (`SemanticSparqlRest`) | NO | Read-only. `SparqlQueryValidator` explicitly rejects `INSERT / DELETE / CONSTRUCT / UPDATE / LOAD` forms (400 with `urn:shepard:error:sparql.read-only`). |
| n10s HTTP port (`http://localhost:7474/rdf/neo4j/import`) | NO | Internal-only; reachable only from inside the backend pod (`shepard.semantic.internal.http-url` defaults to `http://localhost:7474`). No external route. |
| `POST /shepard/api/semanticRepositories` (`SemanticRepositoryRest`) | n/a | Creates a *catalogue row* (name + endpoint), not a triple store. Not an ingest path. |
| `POST /shepard/api/collections/{c}/dataObjects/{d}/semanticAnnotations` (`DataObjectSemanticAnnotationRest`) | **YES — the only viable shape** | Body = `SemanticAnnotationIO`: `{propertyIRI, valueIRI, propertyRepositoryId, valueRepositoryId, numericValue?, unitIRI?}`. Each annotation is one `(subject=DO, predicate=propertyIRI, object=valueIRI)` triple. PROV-O is preseeded as a built-in bundle (`backend/src/main/resources/ontologies/ontologies-manifest.json` confirms `prov-o` bundle with `iriPrefix: http://www.w3.org/ns/prov#`). |

### Recommendation for v15

**Use `POST /shepard/api/collections/{c}/dataObjects/{do}/semanticAnnotations` to emit PROV-O fragments per-DO.**

Constraints + shape:

- Each annotation is a binary predicate. Compound PROV-O statements (`prov:Activity` with several `prov:used` + `prov:wasInformedBy`) become **N annotations on the same DO** sharing a hidden activity URI.
- `propertyRepositoryId` / `valueRepositoryId` must reference existing `SemanticRepository` rows. PROV-O ships preseeded; query `GET /v2/semantic/repositories` (fork) or `GET /shepard/api/semanticRepositories` to find the bundle's repo id. Source IRIs for v14-migrated entities (e.g. `https://backend.bt-au-cube3.intra.dlr.de/shepard/api/collections/48297/dataObjects/<srcId>`) need a **`migration-source` SemanticRepository** created once at v15 start via `POST /shepard/api/semanticRepositories` so `valueRepositoryId` resolves.
- Auth scope: any authenticated caller with write rights on the DO. No admin role required.
- `numericValue` + `unitIRI` (QA-1 extension) carry the optional unit-aware value — convenient for ETA / duration emissions.

Per-import PROV-O graph (v15 emits):

```turtle
# Each migrated DO gets ≥2 annotations:
<thisDO>  prov:wasDerivedFrom  <sourceDO> .
<thisDO>  prov:wasGeneratedBy  <importSession> .
# Optional, when src has predecessor link:
<thisDO>  prov:wasInformedBy   <predDO> .
```

Wire:

```bash
curl -X POST \
  -H 'Content-Type: application/json' \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$DEST/shepard/api/collections/$COLL_ID/dataObjects/$DO_ID/semanticAnnotations" \
  -d '{
    "propertyIRI": "http://www.w3.org/ns/prov#wasDerivedFrom",
    "valueIRI":    "urn:shepard:src:48297:dataObject:12345",
    "propertyRepositoryId": <PROV_O_REPO_ID>,
    "valueRepositoryId":    <MIGRATION_SOURCE_REPO_ID>
  }'
```

Failure modes:
- `400` if `propertyIRI` or `valueIRI` is blank, or repository ids are missing.
- `404` if either repository id is unknown.
- `409` if duplicate annotation on the same DO (verify against `SemanticAnnotationDAO`; may be idempotent or may collide).

### Fork follow-up (NOT v15 scope)

A proper Turtle/N-Triples PROV-O ingest needs a new endpoint:
```
POST /v2/provenance/import
Content-Type: text/turtle  (or application/n-triples)
Body: <turtle bytes>
```
Backed by `n10s.rdf.import.inline` (same call the `OntologySeedService` uses for ontologies, but writing to a **session-scoped named graph** so import-time provenance doesn't pollute the ontology graph). Track this as a separate PR — it's the right shape but it's net-new surface.

## Patch plan

For each bug, the literal change. Line numbers reference `mffd-dropbox-import.py` v14.

### A + G — TS reference creation order + body shape

**Replace** `link_ts_to_do` (lines 591–598) **with**:

```python
def link_ts_to_do(self, coll_id: int, do_id: int, container_id: int, name: str,
                  timeseries: list[dict], start_ms: int, end_ms: int) -> int | None:
    """Create a timeseriesReference AFTER the container has channels.

    timeseries: list of {measurement, device, location, symbolicName, field}
    discovered from GET /timeseriesContainers/{cid}/timeseries.
    """
    if not timeseries:
        print(f"  [ts-link] refusing to link {name!r} — container has no channels")
        return None
    url = (f"{self._base}/shepard/api/collections/{coll_id}"
           f"/dataObjects/{do_id}/timeseriesReferences")
    body = {
        "name": name,
        "timeseriesContainerId": container_id,
        "start": start_ms,
        "end":   end_ms,
        "timeseries": timeseries,  # ← required, minItems:1
    }
    r = self._post(url, body)
    return r.json().get("id") if r else None
```

**Add** helper:

```python
def list_ts_channels(self, container_id: int) -> list[dict]:
    """GET /timeseriesContainers/{cid}/timeseries → channel 5-tuples."""
    r = self._get(f"{self._base}/shepard/api/timeseriesContainers/{container_id}/timeseries")
    if r is None: return []
    out = []
    for ch in r.json():
        out.append({
            "measurement":  ch["measurement"],
            "device":       ch["device"],
            "location":     ch["location"],
            "symbolicName": ch["symbolicName"],
            "field":        ch["field"],
        })
    return out
```

**Reorder** the per-step flow in `run_source_mode` (around lines 1176–1290):
1. Create shared TS container — do NOT link yet.
2. Loop source DOs, export+import every TS ref's CSV into the container.
3. After the loop, `channels = dest_client.list_ts_channels(ts_container_id)`.
4. Compute `start_ms`/`end_ms` from the channel data (`GET .../timeseriesContainers/{cid}/timeseries/{tsId}` returns min/max, or use a wide bracket like `[0, 2**62]` if uncertain).
5. **Then** call `link_ts_to_do(... , timeseries=channels, start_ms=…, end_ms=…)`.

### B + D — Structured upload uses POST with wrapper

**Replace** `upload_structured_payload` (lines 638–647) **with**:

```python
def upload_structured_payload(self, container_id: int,
                              payload: dict | list, name: str = "payload") -> str | None:
    """POST a JSON payload to a structured-data container. Returns oid on success."""
    import json as _json
    url = f"{self._base}/shepard/api/structuredDataContainers/{container_id}/payload"
    body = {
        "structuredData": {"name": name},
        "payload": _json.dumps(payload),   # ← required: STRING, minLength:2
    }
    r = self._request_with_retry("POST", url, json=body, timeout=60)
    if r is None or not r.ok:
        if r: self._log_err("POST (structured)", url, r)
        return None
    return r.json().get("oid")
```

### C — link_structured_to_do needs structuredDataOids[]

**Replace** `link_structured_to_do` (lines 627–636) **with**:

```python
def link_structured_to_do(self, coll_id: int, do_id: int, container_id: int,
                          name: str, oids: list[str]) -> bool:
    """Create a structuredDataReference. `oids` MUST be non-empty (minItems:1)."""
    if not oids:
        print(f"  [sd-link] refusing to link {name!r} — no oids")
        return False
    url = (f"{self._base}/shepard/api/collections/{coll_id}"
           f"/dataObjects/{do_id}/structuredDataReferences")
    body = {
        "name": name,
        "structuredDataContainerId": container_id,
        "structuredDataOids": oids,   # ← required, minItems:1
    }
    r = self._post(url, body)
    return r is not None
```

**Reorder** the structured-data block in `run_source_mode` (lines 1292–1321):
1. Create container.
2. `oids = []`; for each downloaded payload: `oid = upload_structured_payload(container_id, payload, sd_ref.name)`; `oids.append(oid)`.
3. After loop: `link_structured_to_do(... oids=oids)`.

### E + F — Multi-OID source download

**Replace** `_fetch_file_refs` (lines 281–294) **with**:

```python
def _fetch_file_refs(self, coll_id: int, do_id: int) -> list[FileRef]:
    r = self._get(f"{self._base}/shepard/api/collections/{coll_id}"
                  f"/dataObjects/{do_id}/fileReferences")
    if r is None: return []
    refs = []
    for item in r.json():
        oids = item.get("fileOids") or []
        for oid in oids:
            refs.append(FileRef(
                fref_id=item["id"], name=item.get("name") or f"file-{item['id']}",
                size=0, oid=oid))
    return refs
```

Add `oid: str = ""` to the `FileRef` dataclass. **Replace** `download_file_ref` URL (line 370) with the per-oid path:

```python
url = (f"{self._base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}"
       f"/fileReferences/{fref_id}/payload/{oid}")
```

**Replace** `_fetch_structured_refs` + `download_structured` similarly: iterate `structuredDataOids[]`, GET `/structuredDataReferences/{refId}/payload/{oid}` per oid, parse `StructuredDataPayload` (`{structuredData, payload:string}`), `json.loads(wrapper["payload"])` to reconstruct the original payload.

### H — Drop csv_format=WIDE

**Line 332**: change `params={"csv_format": "WIDE"}` to `params={"csv_format": "ROW"}` (or omit entirely — ROW is the default). The fork's `WIDE` extension does not exist on the v5.4.0 source.

### I — Predecessor wiring via DataObject body

**Delete** `_link_predecessor` (lines 479–488). **Change** `create_data_object` (lines 458–477) to include `predecessorIds` directly in the POST body:

```python
def create_data_object(self, coll_id, name, description="", attrs=None,
                       predecessor_id=None):
    body = {"name": name}
    if description: body["description"] = description
    if attrs:       body["attributes"]  = attrs
    if predecessor_id is not None:
        body["predecessorIds"] = [predecessor_id]
    r = self._post(f"{self._base}/shepard/api/collections/{coll_id}/dataObjects", body)
    return r.json() if r else None
```

For wiring predecessors **after** creation (the v15 cross-step case), use PUT on the DataObject body:

```python
def set_predecessors(self, coll_id, do_id, pred_ids: list[int]) -> bool:
    # GET full DO first (preserve fields), then PUT with predecessorIds set.
    r = self._get(f"{self._base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}")
    if r is None: return False
    body = r.json()
    body["predecessorIds"] = pred_ids
    # Strip readOnly fields the server rejects on PUT:
    for k in ("id","createdAt","createdBy","updatedAt","updatedBy","collectionId",
              "referenceIds","successorIds","childrenIds","parentId","incomingIds"):
        body.pop(k, None)
    return self._put(f"{self._base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}", body) is not None
```

### J — Two-step v1 file upload

**Replace** the v1 fallback in `upload_file` (lines 555–578) with two steps:

```python
# 1. multipart → /fileContainers/{cid}/payload — get oid
container_id = self.get_or_create_default_file_container(coll_id)
r1 = self._s.post(
    f"{self._base}/shepard/api/fileContainers/{container_id}/payload",
    files={"file": (display, wrapped)}, timeout=600)
if not r1.ok:
    self._log_err("POST fc/payload", r1.url, r1); return False
oid = r1.json().get("oid")

# 2. JSON → fileReferences with fileOids:[oid]
r2 = self._post(
    f"{self._base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}/fileReferences",
    {"name": display, "fileOids": [oid], "fileContainerId": container_id})
return r2 is not None
```

`get_or_create_default_file_container` reads `Collection.defaultFileContainerId` (already in spec) or creates one via `POST /fileContainers` and stores it back on the collection.

### K — Drop currentUser fallback

**Lines 219–224**: drop the `/shepard/api/users/currentUser` fallback. Either `/v2/users/me` succeeds (fork) or warmup fails fast against an unsupported source. The v2 path on the fork is the only thing worth probing.

### L — Stop sending readOnly fields on create

**Line 587**: change `{"name": name, "type": "TIMESERIES"}` to `{"name": name}`. Container type is fixed by the path.

### R — Add a source-side warmup

In `main()`, after constructing `source_client`, call `source_client.warmup_source()` — a minimal probe: `GET /shepard/api/collections?page=0&size=1`. If 401/403/404 fail fast with a clear error.

### PROV-O writeback (new in v15)

**Add** to `ShepardClient`:

```python
def get_or_create_repo(self, name: str, type_: str, endpoint: str | None = None) -> int | None:
    """Idempotent: find by name, else create. Returns the repository id."""
    r = self._get(f"{self._base}/shepard/api/semanticRepositories", {"name": name})
    if r is not None:
        for repo in r.json():
            if repo.get("name") == name:
                return repo.get("id")
    body = {"name": name, "type": type_}
    if endpoint: body["endpoint"] = endpoint
    r = self._post(f"{self._base}/shepard/api/semanticRepositories", body)
    return r.json().get("id") if r else None

def add_semantic_annotation(self, coll_id: int, do_id: int,
                            property_iri: str, value_iri: str,
                            property_repo_id: int, value_repo_id: int,
                            numeric_value: float | None = None,
                            unit_iri: str | None = None) -> bool:
    url = (f"{self._base}/shepard/api/collections/{coll_id}"
           f"/dataObjects/{do_id}/semanticAnnotations")
    body = {
        "propertyIRI": property_iri,
        "valueIRI":    value_iri,
        "propertyRepositoryId": property_repo_id,
        "valueRepositoryId":    value_repo_id,
    }
    if numeric_value is not None: body["numericValue"] = numeric_value
    if unit_iri:                  body["unitIRI"] = unit_iri
    return self._post(url, body) is not None

def emit_prov_for_migration(self, coll_id: int, dest_do_id: int,
                            src_coll_id: int, src_do_id: int,
                            session_id: str,
                            prov_repo_id: int, migration_repo_id: int,
                            pred_do_id: int | None = None) -> None:
    src_uri = f"urn:shepard:src:{src_coll_id}:dataObject:{src_do_id}"
    sess_uri = f"urn:shepard:import:session:{session_id}"
    PROV = "http://www.w3.org/ns/prov#"
    self.add_semantic_annotation(coll_id, dest_do_id,
        PROV + "wasDerivedFrom", src_uri, prov_repo_id, migration_repo_id)
    self.add_semantic_annotation(coll_id, dest_do_id,
        PROV + "wasGeneratedBy", sess_uri, prov_repo_id, migration_repo_id)
    if pred_do_id is not None:
        pred_uri = f"urn:shepard:dataObject:{pred_do_id}"
        self.add_semantic_annotation(coll_id, dest_do_id,
            PROV + "wasInformedBy", pred_uri, prov_repo_id, migration_repo_id)
```

Bootstrap call once per v15 run:

```python
prov_repo_id      = client.get_or_create_repo("prov-o", "INTERNAL")  # ships preseeded; lookup
migration_repo_id = client.get_or_create_repo(
    f"mffd-migration-{SESSION_ID}", "SPARQL",  # placeholder type; values are opaque urns
    endpoint=f"urn:shepard:migration:{SESSION_ID}")
```

(If `INTERNAL` lookup of preseeded `prov-o` fails the design must fall back to creating a SPARQL-typed catalogue row that points at the n10s endpoint — verify against `OntologySeedService` whether the prov-o bundle ships a `SemanticRepository` row or only ontology triples.)

## What I'd remove from v14 entirely

- **`_link_predecessor`** (lines 479–488) — phantom endpoint. Predecessors belong inside the DataObject body.
- **`/shepard/api/users/currentUser` fallback** (lines 222–224) — endpoint never existed in upstream v5.4.0.
- **v1 multipart-to-fileReferences fallback** (lines 555–578) — wrong wire shape. Two-step (fileContainer/payload → fileReferences JSON) is the only correct v1 flow.
- **`csv_format=WIDE`** (line 332) — invalid enum on the source. ROW is fine; WIDE is a fork-only invention.
- **Linear name-filter loops** in `find_collection` / `find_data_object` (lines 404–407, 453–456) — the server already filters by `name`.
- **`"type": "TIMESERIES"`** in `create_ts_container` body (line 587) — readOnly field.
- **`probe_structured` / `probe_timeseries`** as currently written (lines 696–739) — both send wrong-shape bodies guaranteed to 400. Either drop them or rewrite to match the schemas (Bug O, P).

Together these cut ~80 lines and eliminate four classes of silent / noisy failure.
