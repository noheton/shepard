# 91 — appId URI Scheme: HTTPS Persistent Identifiers

**Status:** Designed  
**Depends on:** L2d (v2 API shelf), 87 (timeseries appId migration)  
**Related:** 25 (Neo4j id migration), 85 (OpenAPI / LLM readable)

---

## 1. Problem

Shepard's `appId` is a bare UUID v7: `019e30b0-9e96-7ef6-ad1e-6221d6220244`.

A bare UUID is not a persistent identifier in the linked-data or FAIR sense:

- It is not dereferenceable — a third party cannot resolve it to data without knowing the instance URL.
- It cannot be used as an RDF subject URI.
- It cannot appear in DataCite metadata, schema.org records, or OpenAIRE exports without wrapping it in a URL anyway — and different exporters wrap it differently, producing inconsistent PIDs for the same entity.
- If an operator migrates the instance to a new hostname, all previously-issued identifiers silently break.

## 2. Decision: HTTPS URI with `/id/` namespace

Every Shepard entity gets a stable HTTPS persistent identifier rooted at the instance's public URL:

```
https://<instance-root>/id/<type>/<appId>
```

Examples for `shepard.nuclide.systems`:

```
https://shepard.nuclide.systems/id/collection/019e30b0-0000-7ef6-ad1e-6221d6220000
https://shepard.nuclide.systems/id/dataobject/019e30b0-9e96-7ef6-ad1e-6221d6220244
https://shepard.nuclide.systems/id/timeseries/019e30b0-a37c-7745-8fa8-79e518d0e318
https://shepard.nuclide.systems/id/file/019e30b0-beef-7ef6-ad1e-6221d6220999
https://shepard.nuclide.systems/id/structured/019e30b0-cafe-7ef6-ad1e-6221d6220abc
```

### Why HTTPS URI over alternatives

| Scheme | Dereferenceable | No external minter | RDF subject | FAIR F1/A1 |
|--------|----------------|-------------------|-------------|------------|
| Bare UUID v7 (current) | ✗ | ✓ | ✗ | F1 only |
| `tag:` (RFC 4151) | ✗ | ✓ | ✗ | F1 only |
| `urn:<nid>:` (RFC 8141) | ✗ (no resolver) | ✗ (IANA reg) | ✗ | F1 only |
| `https://<root>/id/` (this doc) | **✓** | **✓** | **✓** | **F1 + A1** |

The instance's DNS authority is the root. No external PID service is required at runtime. The UUID v7 payload stays unchanged — it is the opaque unique token; the URI wrapping is the stable identity surface.

---

## 3. Wire format: no breaking change

The `appId` field in all API responses **stays as the bare UUID**:

```json
{ "appId": "019e30b0-9e96-7ef6-ad1e-6221d6220244" }
```

The URI form is an **additive field** for linked-data consumers. In v2 responses that carry it, it appears as `uri`:

```json
{
  "appId": "019e30b0-9e96-7ef6-ad1e-6221d6220244",
  "uri":   "https://shepard.nuclide.systems/id/dataobject/019e30b0-9e96-7ef6-ad1e-6221d6220244"
}
```

`uri` is computed server-side from `appId` + the configured instance base URL (`shepard.instance.base-url`). It is never stored in Neo4j — it is a derived field.

Clients that don't need linked-data identifiers ignore `uri`. Clients that do (exporters, MCP, semantic reasoning) use it as the canonical `@id`.

---

## 4. Redirect endpoint — `GET /id/{type}/{appId}`

A new JAX-RS resource at `/id/{type}/{appId}` handles persistent identifier resolution.

### 4.1 Types

| Path segment | Entity |
|---|---|
| `collection` | Collection |
| `dataobject` | DataObject |
| `timeseries` | TimeseriesContainer |
| `file` | FileContainer |
| `structured` | StructuredDataContainer |

### 4.2 Content negotiation

The redirect target depends on the `Accept` header:

| Accept | Response | Location |
|---|---|---|
| `application/json` (or none) | `303 See Other` | `/v2/collections/{col}/data-objects/{do}` (resolved via appId lookup) |
| `text/html` | `303 See Other` | UI deep-link `/collections/{col}/dataobjects/{do}` |
| `application/ld+json` | `200 OK` | JSON-LD record (future — Phase 2) |

The redirect is `303 See Other` (not `301`/`302`) because the identifier names a *thing*, not a document — standard Linked Data practice (W3C Cool URIs for the Semantic Web).

### 4.3 Endpoint spec

```
GET /id/{type}/{appId}
```

**Path params:**
- `type` — one of the values in §4.1
- `appId` — UUID v7 string

**Headers:**
- `Accept` — content negotiation (optional; defaults to `application/json`)

**Responses:**

| Status | Condition |
|---|---|
| `303 See Other` | Entity found; `Location` header set |
| `404 Not Found` | `appId` does not exist or `type` is wrong |
| `401 Unauthorized` | No valid credentials (same auth as all other endpoints) |
| `403 Forbidden` | Caller lacks read access to the entity |

**Example:**

```http
GET /id/dataobject/019e30b0-9e96-7ef6-ad1e-6221d6220244
Accept: application/json
Authorization: Bearer <token>

HTTP/1.1 303 See Other
Location: https://shepard.nuclide.systems/v2/collections/019e30b0-0000.../data-objects/019e30b0-9e96...
```

### 4.4 Implementation notes

- The endpoint needs to resolve `dataObjectAppId` → `(collectionAppId, dataObjectAppId)` because v2 endpoints are collection-scoped. This requires a single index lookup in Neo4j by `appId` property.
- For `collection` type: redirect to `/v2/collections/{appId}`.
- For `timeseries`, `file`, `structured`: redirect to the container's own v2 endpoint. If no collection-scoped path exists yet, redirect to `/v2/containers/<type>/{appId}` when that shelf exists; until then, `303` to the containing DataObject.
- This endpoint is the one place in Shepard that crosses type boundaries — it must be registered outside the `collections/{col}/...` hierarchy. Register it at the root resource level alongside `/health` and `/admin`.

---

## 5. Configuration

One new property seeds the URI base. It is **deploy-time-only** (not runtime-mutable) because changing it mid-flight invalidates all previously issued URIs:

```properties
# application.properties
shepard.instance.base-url=https://shepard.nuclide.systems
```

No default. Startup fails with a clear error if not set (the minter cannot produce valid URIs without it). Operators upgrading from upstream must add this key — tracked in `aidocs/34`.

The server computes the full URI as:

```java
String uri = baseUrl + "/id/" + type.pathSegment() + "/" + appId;
```

---

## 6. JSON-LD export (Phase 2, not blocking)

When `Accept: application/ld+json` is requested, the endpoint returns a minimal JSON-LD record using schema.org + DataCite vocabulary:

```json
{
  "@context": {
    "@vocab": "https://schema.org/",
    "datacite": "http://purl.org/spar/datacite/"
  },
  "@id": "https://shepard.nuclide.systems/id/dataobject/019e30b0-9e96-7ef6-ad1e-6221d6220244",
  "@type": "Dataset",
  "name": "TR-004",
  "description": "Hotfire test run 4 — anomaly",
  "dateCreated": "2024-06-02T10:15:00Z",
  "isPartOf": {
    "@id": "https://shepard.nuclide.systems/id/collection/019e30b0-0000-7ef6-ad1e-6221d6220000"
  },
  "creator": {
    "@type": "Person",
    "identifier": "https://orcid.org/0000-0002-1234-5678"
  }
}
```

This is the layer that enables OpenAIRE, re3data, and Helmholtz Databus harvesting without a dedicated exporter plugin.

---

## 7. FAIR compliance gains

| FAIR dimension | Before | After |
|---|---|---|
| **F1** — globally unique identifier | UUID v7 (unique but not globally resolvable) | HTTPS URI (globally unique + rooted in DNS authority) |
| **F4** — registered in searchable resource | Not applicable | URI can be submitted to re3data, OpenAIRE, Databus |
| **A1** — retrievable by identifier using standard protocol | No (bare UUID has no protocol) | Yes — `GET /id/...` returns 303 to data |
| **I1** — uses formal knowledge representation language | No | JSON-LD `@id` enables RDF graph participation (Phase 2) |
| **R1.1** — license attached to data | Not changed | URI is the anchor point for license metadata in JSON-LD |

---

## 8. What this enables downstream

- **MCP tool results** — `uri` field in `get_data_object` / `get_collection` responses gives Claude a dereferenceable link it can cite.
- **shepard-plugin-publisher** — exporter uses `uri` as the `@id` in DataCite XML; no per-exporter URL-construction logic needed.
- **Helmholtz Unhide** — harvest feed can include `uri` as the `sameAs` link, letting Databus reconcile instances.
- **Handle / ePIC overlay** — if an operator later registers a Handle, they point it at `/id/...`; the UUID layer absorbs any future internal restructuring.
- **Semantic reasoning** — ontology triples can reference entities by stable URI; queries like "all DataObjects that are `prov:wasInfluencedBy` TR-004" become expressible in SPARQL.

---

## 9. Migration path (upgrade from upstream / current fork)

| Step | Who | What |
|---|---|---|
| Add `shepard.instance.base-url` | Operator | One new property in `application.properties` |
| Deploy new build | Operator | `/id/` endpoint goes live |
| Existing `appId` values | None | Unchanged — no data migration |
| Existing API clients | None | `appId` field unchanged; `uri` is additive |
| CLAUDE.md / 34 tracker | Dev | Entry added: additive endpoint, one new required config key |

No database migration required. No client breakage.

---

## 10. Open questions

1. **Auth on `/id/` for public datasets** — should the redirect work without credentials for DataObjects marked `publicationState: OPEN`? Strawman: yes — unauthenticated `GET /id/...` returns `303` for public entities, `401` for restricted ones. This aligns with FAIR A1.2 (open, free, universally implementable protocol).

2. **Tombstone on deletion** — if a DataObject is deleted, should `/id/...` return `410 Gone` with a minimal record? (Standard Linked Data practice.) Deferred to Phase 2 but should be in the spec before the endpoint ships.

3. **Container types without collection scope** — timeseries / file / structured containers are always accessed via a parent DataObject in v2. The redirect for these types is currently "redirect to containing DataObject." Should we add direct container endpoints (`/v2/containers/timeseries/{appId}`) as part of this work, or keep them collection-scoped?
