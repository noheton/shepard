# `/v2/` API Simplification — Flat Paths, Output Profiles, MCP-Friendly OpenAPI

**Status.** Concept design.
**Snapshot date.** 2026-05-12.
**Audience.** Contributors. Folds three user asks into one design:
appId-indexed flat paths, output profiles, MCP-friendly OpenAPI.

**Originating items.** User session: (a) "should we remove
quasi-redundant collection and dataObject parameters
`/collection/{colId}/dataObjects/{did}` — `{did}` alone would
suffice", (b) "output control profiles like `metadata`, `relations`,
`all` could be useful for different use cases", (c) "document all
API endpoints in a way a later agent / MCP server can be easily
created." Couples to `aidocs/25 §3.2 / §Phase 4` (L2d — the `/v2/`
shelf with `appId` as native id), `aidocs/47 §4` (DX series — the
codegen archetype DX3 is where `mvn shepard:generate-mcp-server`
lives), `aidocs/51` (instance-admin role — `x-mcp-side-effects:
admin` should imply `@RolesAllowed("instance-admin")`),
`aidocs/49 §2.2` (in-app docs read the OpenAPI source today),
`aidocs/23` (the original API critique that listed the redundant
path-param pain point), `CLAUDE.md` API-version policy (the
`/shepard/api/` surface stays byte-frozen; everything new lives at
`/v2/`).

---

## 1. Where we are

### 1.1 The nesting today

Today's `/shepard/api/...` surface is heavily-nested. A typical
single-entity path looks like:

```
GET    /shepard/api/collections/{collectionId}/dataObjects/{dataObjectId}/fileReferences/{fileReferenceId}
```

That URL carries **three** numeric path params for what is, at the
database layer, a single-row lookup keyed by the inner id. The
outer two are evidence of upstream's REST-as-tree style: the path
mirrors the entity-graph parent chain.

This was fine when an internal Neo4j `long` was the only id we
could expose — the `long` was reused on delete, so naming
`collectionId + dataObjectId + fileReferenceId` together gave the
caller a fighting chance of catching a stale-id mismatch (one of
the three would 404 first). Post-L2c (`aidocs/25 §Phase 3`), every
node carries a globally-unique `appId` (UUID v7); the outer two
ids are **redundant lookup-key noise**.

### 1.2 Five-to-seven places where the redundancy bites

Grepped from the current resource set
(`grep '@Path' backend/src/main/java/de/dlr/shepard/**/*Rest.java`):

| # | Current path | Redundancy |
|---|---|---|
| 1 | `GET /shepard/api/collections/{collectionId}/dataObjects/{dataObjectId}` (`DataObjectRest:124`) | `dataObjectId` alone resolves; `collectionId` is verified-but-discarded after the lookup. |
| 2 | `PUT /shepard/api/collections/{collectionId}/dataObjects/{dataObjectId}` (`DataObjectRest:179`) | Same. The body carries no parent reference; the parent is identified by URL only. |
| 3 | `GET /shepard/api/collections/{collectionId}/dataObjects/{dataObjectId}/fileReferences/{fileReferenceId}` (`FileReferenceRest:101`) | Three-deep path for a single-row lookup. The inner id alone is sufficient. |
| 4 | `GET .../timeseriesReferences/{timeseriesReferenceId}` (`TimeseriesReferenceRest:97`) | Same shape, four references reachable this way (file / timeseries / structured / spatial). |
| 5 | `GET .../timeseriesReferences/{tsRefId}/payload` (`TimeseriesReferenceRest:179`) | Three context params plus a sub-resource segment; only `{tsRefId}` is load-bearing. |
| 6 | `GET .../dataObjects/{did}/semanticAnnotations/{annId}` (`DataObjectSemanticAnnotationRest:94`) | Same anti-pattern reproduces for the semantic-annotation slice. |
| 7 | `GET /shepard/api/collections/{collectionId}/versions/{versionUid}` (`CollectionVersioningRest:77`) | Versions live under a collection, but a `versionUid` is already globally unique; the parent path is decorative. |

The pattern reproduces on every reference kind. Counting *only*
single-entity GET/PUT/DELETE handlers (i.e. excluding bulk
listings — see §2.2 for why those stay), this is the shape of
roughly **two-thirds of the resource layer**.

### 1.3 Why this hurts callers

- **Wrong-parent-id 404.** A user pastes a DataObject's `did` from
  the UI and an old `colId` they remember; the request 404s with
  no hint which id is the bad one. The "use the body's
  `parentCollection.appId`" workaround works only if the user
  already loaded the DataObject — the kind of chicken-and-egg
  that costs a casual user half an hour.
- **Frontend coupling.** Every link or button in the frontend has
  to thread both ids through props / route state. A "navigate to
  this DataObject" deep-link cannot be a single id; it has to
  carry the parent too. `aidocs/33` calls out three workflows
  where this multiplies click-cost (W2, W5, W11).
- **MCP / agent latency.** An agent that finds a DataObject via
  search (one id) and then wants to PATCH it has to round-trip
  back to fetch the parent id. Two API calls where one would do.
- **Client codegen verbosity.** OpenAPI Generator emits one
  argument per `{pathParam}`; the Python and TypeScript clients
  end up with 4-arg method signatures for what is, semantically,
  a one-arg lookup.

This is the substrate the present design fixes.

---

## 2. The simplification proposal — flat `appId`-indexed paths

### 2.1 The single-entity rule

For **single-entity** GET / PATCH / PUT / DELETE, `/v2/...` paths
are flat:

```
GET    /v2/dataobjects/{appId}
PATCH  /v2/dataobjects/{appId}
DELETE /v2/dataobjects/{appId}

GET    /v2/file-references/{appId}
GET    /v2/file-references/{appId}/payload
PUT    /v2/file-references/{appId}/payload

GET    /v2/timeseries-references/{appId}
GET    /v2/timeseries-references/{appId}/payload
GET    /v2/timeseries-references/{appId}/export

GET    /v2/structured-data-references/{appId}
GET    /v2/spatial-data-references/{appId}
GET    /v2/semantic-annotations/{appId}
GET    /v2/versions/{versionUid}
GET    /v2/permissions/{appId}
GET    /v2/subscriptions/{appId}
```

No parent-id segments. The `appId` is globally unique (per
`aidocs/25 §2`); the row resolves in one indexed Cypher lookup
(`MATCH (e) WHERE e.appId = $appId`).

Parent context is still **visible in the response body**, as a
structured `parentCollection: { appId, name }` /
`parentDataObject: { appId, name }` field, for UI navigation and
RO-Crate-style breadcrumbs:

```json
{
  "appId": "01938f4e-1234-7abc-...",
  "name": "Sensor batch 2026-05-08",
  "parentCollection": {
    "appId": "01938f4d-aaaa-7abc-...",
    "name": "LUMEN run 47"
  },
  "createdAt": "2026-05-08T09:14:22Z",
  ...
}
```

The parent is part of the **representation**, not the URL.
Hypermedia traditionalists call this a regression; the user
question explicitly accepts it ("even if it is not as 'resty'?").

### 2.2 Where the nested path stays useful

The redundancy only exists on *single-entity* endpoints.
**Bulk listings are genuinely scoped** — `GET .../dataobjects`
under a Collection answers a different question than
`GET /v2/dataobjects` (which would list every DataObject the
caller can read across every Collection):

| Endpoint | Stays nested? | Why |
|---|---|---|
| `GET /v2/collections/{appId}/dataobjects` | **Yes — nested.** | Scoped listing under a parent. The `{appId}` selects the scope; without it, the endpoint means something different. |
| `POST /v2/collections/{appId}/dataobjects` | **Yes — nested.** | Create-under-parent. The parent appears in the URL so the parent-context is unambiguous at create time. |
| `GET /v2/dataobjects/{appId}` | **No — flat.** | Single-entity lookup. `appId` alone resolves. |
| `GET /v2/dataobjects/{appId}/file-references` | **Yes — nested.** | Scoped listing under a parent. The parent is the DataObject. |
| `POST /v2/dataobjects/{appId}/file-references` | **Yes — nested.** | Create-under-parent. |
| `GET /v2/file-references/{appId}` | **No — flat.** | Single-entity lookup. |
| `PATCH /v2/file-references/{appId}` | **No — flat.** | Single-entity mutation. |
| `DELETE /v2/file-references/{appId}` | **No — flat.** | Single-entity mutation. |

**Rule of thumb.** A path segment that names a *parent* and is
followed by `s` (a plural collection-name segment) carries
information. A path segment that names a parent and is followed
by another `{appId}` is redundant.

### 2.3 Tradeoffs

Honest list, not advocacy:

| Pro | Con |
|---|---|
| Half the path-param surface; clients are quieter. | Less REST-traditional. Some tooling (HAL-Forms?) expects a parent chain. |
| `appId` round-tripped through a search result, an SSE event, or an MCP tool call resolves in one request. | Some callers will paste a flat URL into a system that wants to introspect the parent; they'll need to GET-then-read-the-body. |
| Codegen produces one-arg method signatures for single-entity ops. | Three of today's URL-template generators (OpenAPI-Generator Java, Kiota, openapi-typescript) need a config tweak to dedupe path-param names that now appear in only one place — minor. |
| Eliminates the wrong-parent-id 404 class. | We don't get the URL-as-breadcrumb property for free; the frontend has to load the entity and read `parentCollection.appId` to build a breadcrumb. (Cheap: one round-trip the frontend already does.) |
| Migration story: trivial. The flat path is additive; the nested path stays alive as long as `/shepard/api/...` stays alive (which is "forever-ish" per `CLAUDE.md`). | Two URL shapes for the same row means two cache entries in CDN / proxy / browser-history. Minor; both surfaces stay consistent. |

The decision lever: **single-entity endpoints flatten; listings
and create-under-parent stay nested**. That keeps the part of
REST that carries information and drops the part that's noise.

### 2.4 What `/shepard/api/...` does

It stays byte-frozen, per `CLAUDE.md`'s API-version policy. The
nested upstream paths keep working. A client built against
`gitlab.com/dlr-shepard/shepard 5.2.0` runs unmodified.

A caller who wants the flat surface opts in by hitting `/v2/...`
The two surfaces share the same underlying services; the resource
classes thinly forward to identical service-layer methods.

---

## 3. Output-control profiles — the `?profile=` query parameter

### 3.1 Three v1 profiles

The same DataObject can be wanted three ways:

- A tree-view in the UI wants just the name + appId + a few flags.
- A graph-navigation step wants the entity plus the **ids** of
  everything it links to (so the caller can decide what to fetch
  next, without paying for the nested-object payload up-front).
- The RO-Crate exporter wants everything — metadata, every
  relation expanded, every nested object inline.

One endpoint shape, three profiles:

| Profile | What's in the response | Use case |
|---|---|---|
| `metadata` | The entity's own scalar fields only. No relations, no nested children. Smallest payload (typically 200-500 bytes for a DataObject). | Tree-views; quick "does it exist?" checks; bulk listings where the caller only needs names. |
| `relations` | Metadata + relationship **ids** (linked-entity `appId`s as opaque strings, plus the relation name). No nested objects expanded. | Graph navigation; "what does this link to?"; building a dependency graph client-side. |
| `all` *(default)* | Metadata + nested relation **objects** (the current shape). | Backward-compat default; RO-Crate export; first-load of a detail page. |

Example response shape for a DataObject at the three profiles:

```http
GET /v2/dataobjects/{appId}?profile=metadata
```
```json
{
  "appId": "01938f4e-...",
  "name": "Sensor batch 2026-05-08",
  "createdAt": "2026-05-08T09:14:22Z",
  "createdBy": "alice"
}
```

```http
GET /v2/dataobjects/{appId}?profile=relations
```
```json
{
  "appId": "01938f4e-...",
  "name": "Sensor batch 2026-05-08",
  "createdAt": "2026-05-08T09:14:22Z",
  "createdBy": "alice",
  "parentCollection": "01938f4d-...",
  "fileReferences":      ["01938f50-...", "01938f51-..."],
  "timeseriesReferences": ["01938f52-..."],
  "semanticAnnotations":  ["01938f60-...", "01938f61-..."]
}
```

```http
GET /v2/dataobjects/{appId}?profile=all
```
```json
{
  "appId": "01938f4e-...",
  "name": "Sensor batch 2026-05-08",
  "createdAt": "2026-05-08T09:14:22Z",
  "createdBy": "alice",
  "parentCollection": { "appId": "01938f4d-...", "name": "LUMEN run 47" },
  "fileReferences":      [ { "appId": "...", "name": "...", "mimeType": "..." }, ... ],
  "timeseriesReferences": [ { "appId": "...", "name": "...", "schema": [...] } ],
  "semanticAnnotations":  [ { "appId": "...", "term": "qudt:Newton", "iri": "..." } ]
}
```

### 3.2 Implementation shape

Two seams; both are conventional Quarkus shapes:

1. **JAX-RS request filter.** A `ContainerRequestFilter`
   inspects `?profile=`; populates a request-scoped
   `@RequestScoped class OutputProfile { Kind kind; }` bean.
   Default value: `Kind.ALL`. Unknown profile → 400 with a
   problem+json body listing valid names (per `aidocs/H4` shape).
2. **Serialiser layer.** Use Jackson `@JsonView` on the DTOs.
   One view per profile (`Views.Metadata.class`,
   `Views.Relations.class`, `Views.All.class`). A
   `ResponseBodyWriterInterceptor` reads the request-scoped
   `OutputProfile` and picks the matching view. Existing
   serializer config stays unchanged for `profile=all` (the
   default).

For the `relations` profile, the serialiser visits relation
fields and emits `appId` strings instead of nested DTOs. The
mapping is one line per relation:

```java
@JsonView(Views.Relations.class)
@JsonSerialize(using = AppIdOnlySerializer.class)
private List<FileReference> fileReferences;
```

`AppIdOnlySerializer` extracts each `FileReference.appId` and
writes the array of strings. Implementation is ~30 LoC; one
small custom serializer covers every relation field via Jackson
property-key-list discovery.

### 3.3 OpenAPI shape

Each output schema declares named views. The Smallrye OpenAPI
processor (`backend/pom.xml:98`) already picks up `@JsonView`
annotations when configured. Generated spec carries the three
shapes per resource (`DataObjectMetadataView`,
`DataObjectRelationsView`, `DataObjectAllView`).

Many client generators support views natively:
- **Kiota** (Microsoft, Python / TS / Go / Java) — supports
  per-operation response variants.
- **OpenAPI Generator** — supports `oneOf` discriminated by
  the `?profile=` parameter; the generator emits one model per
  view in Java and Python.

Where a generator doesn't support views, the fallback is the
default-shape (`profile=all`) — backward-compatible.

### 3.4 Status codes / errors

| Case | Status | Body |
|---|---|---|
| Profile recognised | 200 | filtered body |
| Profile not recognised | 400 | `application/problem+json` listing valid names (per `aidocs/H4` / RFC 7807) |
| Profile omitted | 200 with default (`all`) | full body |

Body for the 400 case:

```json
{
  "type": "https://docs.shepard.dlr.de/errors/unknown-profile",
  "title": "Unknown output profile",
  "status": 400,
  "detail": "Profile 'briefly' is not recognised.",
  "validProfiles": ["metadata", "relations", "all"]
}
```

### 3.5 Future: `profile=custom`

Deferred to a later phase. Shape:

```
GET /v2/dataobjects/{appId}?profile=custom&fields=name,createdBy,fileReferences.appId
```

Ad-hoc field-subset projection; conceptually GraphQL-shaped but
without committing to the GraphQL stack. Out of scope for v1;
the spec leaves room (`profile=custom` is reserved name).

---

## 4. MCP-friendly API documentation

The user wants the API "documented in a way a later agent / MCP
server can be easily created." Decompose.

### 4.1 OpenAPI 3.1 as the single source of truth

Already present. `backend/pom.xml:98` pulls in
`quarkus-smallrye-openapi`; the spec is emitted at
`/q/openapi` (YAML) and `/q/openapi.json` (JSON) for every
running backend. The spec covers both `/shepard/api/...` (the
frozen upstream surface) and `/v2/...` (this fork's
development surface).

No new tooling required. The work is on the **content** of the
spec — operation summaries, descriptions, examples, extensions —
not on the generator.

### 4.2 Operation summaries + descriptions

Every endpoint gets:

- `summary` — **< 80 chars**, agent-readable, action-shaped.
  Examples:
  - Good: "Create a DataObject in the given Collection."
  - Bad: "POST a DataObject." / "createDataObject."
- `description` — 1-3 sentences. Includes the **why**, the
  **input constraints** ("name must be 1-128 chars"), and a
  pointer to the relevant `docs/reference/*.md` page
  (`aidocs/49 §2.2`).

Implemented via Smallrye `@Operation(summary = ..., description = ...)`
annotations on every JAX-RS method. Audit shipped as V2S1d (§6).

### 4.3 Per-operation `x-mcp-tool-name` extension

A custom OpenAPI extension on every operation gives MCP tool
generators a stable, predictable name:

```yaml
paths:
  /v2/dataobjects/{appId}:
    get:
      summary: Get a DataObject by id.
      x-mcp-tool-name: shepard_dataobject_get
      x-mcp-side-effects: read
      ...
```

**Naming convention**: `<service>_<resource>_<verb>`, all
lowercase, underscored. The `<service>` is always `shepard`
(reserves room for federated MCP servers that surface multiple
shepard instances). The `<resource>` is the singular noun.
The `<verb>` is one of `get | list | create | update | delete |
export | payload | render`.

Example tool-name mapping:

| Endpoint | `x-mcp-tool-name` |
|---|---|
| `GET /v2/collections` | `shepard_collection_list` |
| `GET /v2/collections/{appId}` | `shepard_collection_get` |
| `POST /v2/collections` | `shepard_collection_create` |
| `PATCH /v2/collections/{appId}` | `shepard_collection_update` |
| `DELETE /v2/collections/{appId}` | `shepard_collection_delete` |
| `GET /v2/collections/{appId}/export` | `shepard_collection_export` |
| `GET /v2/file-references/{appId}/payload` | `shepard_file_reference_payload` |
| `POST /v2/search` | `shepard_search_query` |
| `POST /v2/admin/instance-admins` | `shepard_instance_admin_grant` |

A scheduled CI check (ArchUnit, §8) enforces uniqueness and
naming-convention conformance.

### 4.4 `x-mcp-side-effects: read | write | admin`

Three-valued extension on every operation:

| Value | Meaning | Examples |
|---|---|---|
| `read` | Idempotent, no state change. MCP server can call without confirmation. | All GETs. |
| `write` | Mutates state owned by the calling user. MCP server should confirm with the human first. | POST / PATCH / DELETE on entities the user already controls. |
| `admin` | Privileged operation that crosses user / instance boundaries. MCP server should require explicit "I am acting as instance-admin" intent. | `POST /v2/admin/instance-admins`, `DELETE /v2/admin/instance-admins/{user}`, feature-toggle mutation. |

ArchUnit rule (per §8 open question 3): every method annotated
`@RolesAllowed("instance-admin")` must carry
`x-mcp-side-effects: admin`. Inverse implication too:
`x-mcp-side-effects: admin` without a role guard is a CI failure.

The MCP-protocol-side meaning: a host (Claude Desktop, Cursor,
custom agent) labels read tools auto-callable, write tools
human-in-the-loop confirmable, admin tools "are you sure?
acting as <user>" gated.

### 4.5 Required-fields discipline

Every input schema marks required fields explicitly via
`@Schema(required = true)` / OpenAPI `required: [...]`. No
implicit "you must include X or Y" — if a constraint can't be
expressed in OpenAPI alone, the `description` says so verbatim:

```yaml
DataObjectIO:
  type: object
  required: [name]
  properties:
    name:
      type: string
      minLength: 1
      maxLength: 128
      description: "Human-readable name. Required at create time."
    attributes:
      type: object
      additionalProperties: { type: string }
      description: "Free-form key/value bag. Optional."
```

### 4.6 Examples on every endpoint

At least one **request example** and one **response example**
per operation. Smallrye `@Examples(...)` supports per-operation
example sets:

```java
@APIResponse(
  responseCode = "200",
  content = @Content(
    schema = @Schema(implementation = DataObjectIO.class),
    examples = @ExampleObject(
      name = "typical",
      summary = "A DataObject in the LUMEN demo Collection",
      value = "{ \"appId\": \"01938f4e-...\", \"name\": \"Sensor batch\", ... }"
    )
  )
)
```

Examples serve double duty: MCP-tool-generation latches onto
them for synthetic-input generation; the in-app `/help`
(`aidocs/49`) renders them under the reference page; the
OpenAPI-driven try-it-out UI seeds the form with them.

### 4.7 A small generator — `mvn shepard:generate-mcp-server`

Per `aidocs/47 DX3`, a Maven plugin reads the OpenAPI spec
(local or fetched from a running instance) and emits a stub
**MCP-server in Python**. One MCP tool per operation. The
generator output is a single-file Python package:

```python
# generated by mvn shepard:generate-mcp-server
# spec source: /q/openapi (build 2026-05-08 / commit abc1234)

from mcp.server import Server
from shepard_client import ShepardClient

server = Server("shepard-mcp")

@server.tool(
    name="shepard_dataobject_get",
    description="Get a DataObject by id.",
    side_effect="read",
)
def shepard_dataobject_get(appId: str, profile: str = "all") -> dict:
    return ShepardClient.get_dataobject(appId, profile=profile)

# ... ~120 tools, one per operation ...
```

Out of scope for v1 *implementation*; in scope for v1 *spec*.
The design names it; ships under V2S1e (§6).

### 4.8 The deliverable

Before the L2d cutover (`aidocs/25 §Phase 4`), every shipped
endpoint has:

1. `summary` (< 80 chars, agent-readable).
2. `description` (1-3 sentences, **why**-shaped).
3. `x-mcp-tool-name` (unique across the spec).
4. `x-mcp-side-effects: read | write | admin` (matches the
   `@RolesAllowed` annotation per the ArchUnit rule).
5. At least one request example (where applicable) and one
   response example.
6. Required fields marked explicitly.

A CI gate parses the emitted spec and fails the build if any
operation is missing one of (1)-(4) or (6). (5) is checked
manually during V2S1d audit because example quality isn't
machine-evaluable.

---

## 5. Concrete endpoint inventory (representative slice)

Compiled from the current resource set. Not exhaustive — picks
representative endpoints across every resource family; the rule
for the rest is "same shape, derived mechanically."

| # | Current (`/shepard/api/...`) | `/v2/` (flat where applicable) | `x-mcp-tool-name` | Side-effect | Profile-aware? |
|---|---|---|---|---|---|
| 1 | `GET /collections` | `GET /v2/collections` | `shepard_collection_list` | `read` | yes |
| 2 | `GET /collections/{cid}` | `GET /v2/collections/{appId}` | `shepard_collection_get` | `read` | yes |
| 3 | `POST /collections` | `POST /v2/collections` | `shepard_collection_create` | `write` | yes (return value) |
| 4 | `PUT /collections/{cid}` | `PATCH /v2/collections/{appId}` | `shepard_collection_update` | `write` | yes (return value) |
| 5 | `DELETE /collections/{cid}` | `DELETE /v2/collections/{appId}` | `shepard_collection_delete` | `write` | n/a |
| 6 | `GET /collections/{cid}/dataObjects` | `GET /v2/collections/{appId}/dataobjects` | `shepard_dataobject_list` | `read` | yes |
| 7 | `GET /collections/{cid}/dataObjects/{did}` | `GET /v2/dataobjects/{appId}` | `shepard_dataobject_get` | `read` | yes |
| 8 | `POST /collections/{cid}/dataObjects` | `POST /v2/collections/{appId}/dataobjects` | `shepard_dataobject_create` | `write` | yes (return value) |
| 9 | `PUT /collections/{cid}/dataObjects/{did}` | `PATCH /v2/dataobjects/{appId}` | `shepard_dataobject_update` | `write` | yes (return value) |
| 10 | `DELETE /collections/{cid}/dataObjects/{did}` | `DELETE /v2/dataobjects/{appId}` | `shepard_dataobject_delete` | `write` | n/a |
| 11 | `GET .../{did}/fileReferences/{frid}` | `GET /v2/file-references/{appId}` | `shepard_file_reference_get` | `read` | yes |
| 12 | `GET .../fileReferences/{frid}/payload/{oid}` | `GET /v2/file-references/{appId}/payload/{oid}` | `shepard_file_reference_payload` | `read` | n/a (binary) |
| 13 | `GET .../timeseriesReferences/{tsid}` | `GET /v2/timeseries-references/{appId}` | `shepard_timeseries_reference_get` | `read` | yes |
| 14 | `GET .../timeseriesReferences/{tsid}/payload` | `GET /v2/timeseries-references/{appId}/payload` | `shepard_timeseries_reference_payload` | `read` | n/a (rows) |
| 15 | `GET .../structuredDataReferences/{sdid}` | `GET /v2/structured-data-references/{appId}` | `shepard_structured_data_reference_get` | `read` | yes |
| 16 | `GET .../dataObjects/{did}/semanticAnnotations/{sid}` | `GET /v2/semantic-annotations/{appId}` | `shepard_semantic_annotation_get` | `read` | yes |
| 17 | `GET /collections/{cid}/versions/{uid}` | `GET /v2/versions/{versionUid}` | `shepard_version_get` | `read` | yes |
| 18 | `GET /collections/{cid}/export` | `GET /v2/collections/{appId}/export` | `shepard_collection_export` | `read` | n/a (RO-Crate ZIP) |
| 19 | `POST /search` | `POST /v2/search` | `shepard_search_query` | `read` | yes (result shape) |
| 20 | *(new, post-A0 / `aidocs/51`)* `POST /v2/admin/instance-admins` | `POST /v2/admin/instance-admins` | `shepard_instance_admin_grant` | `admin` | n/a |
| 21 | *(new, post-A0 / `aidocs/51`)* `DELETE /v2/admin/instance-admins/{username}` | `DELETE /v2/admin/instance-admins/{username}` | `shepard_instance_admin_revoke` | `admin` | n/a |
| 22 | *(new, `aidocs/47 DX7`)* `GET /v2/admin/features` | `GET /v2/admin/features` | `shepard_feature_list` | `admin` | n/a |

**Rule for the rest of the surface** (≈ 130 operations across
the 34 JAX-RS resource classes):

- Single-entity `GET / PATCH / PUT / DELETE` → flatten to
  `/v2/<resource-plural>/{appId}`. Profile-aware.
- Bulk listing under a parent → stays nested at
  `/v2/<parent-plural>/{appId}/<child-plural>`. Profile-aware.
- Create-under-parent → stays nested at
  `/v2/<parent-plural>/{appId}/<child-plural>`. `side_effect:
  write`.
- Sub-resource binary payload (`/payload`, `/export`) → not
  profile-aware (returns bytes, not JSON).
- Admin endpoint → `side_effect: admin`; `@RolesAllowed("instance-admin")`.

---

## 6. Phasing

Six slices under the **V2S1** prefix:

| ID | Slice | Size | Gate |
|---|---|---|---|
| **V2S1a** | OpenAPI extension shape — `x-mcp-tool-name` and `x-mcp-side-effects` defined as extension-point constants; Smallrye annotation processor (or hand-applied via `@Extension(name = "x-mcp-tool-name", value = "shepard_...")`) across all resources. CI gate that every operation carries both. | M | None — landable independently. |
| **V2S1b** | `?profile=` query parameter — JAX-RS request filter + Jackson `@JsonView` discipline on DTOs; `metadata` / `relations` / `all` enum; problem+json on unknown profile. Lands on **both** `/v2/...` and (per open question 1) `/v2/...` only. | M | H4 (RFC 7807 problem+json). |
| **V2S1c** | Flat `/v2/dataobjects/{appId}` and the rest of the single-entity flat endpoints. One resource at a time. Lands alongside L2d. | L | L2d (`aidocs/25 §Phase 4`) — needs `appId` as the public identifier. |
| **V2S1d** | Operation summary / description / example audit. Every operation gets all three; CI gate counts missing entries. | M | V2S1a (so the extension fields are also being audited in the same pass). |
| **V2S1e** | `mvn shepard:generate-mcp-server` plugin skeleton (per `aidocs/47 DX3`). Reads OpenAPI; emits Python MCP server stub. | M | V2S1a + V2S1d (needs the metadata + naming convention). |
| **V2S1f** | RFC 8594 `Sunset` deprecation headers on the legacy nested single-entity endpoints, pointing at the flat `/v2/` equivalents. Long deprecation horizon — `Sunset` date deliberately far. | S | V2S1c. |

**Recommended order**: V2S1a → V2S1b → V2S1d → V2S1c → V2S1e →
V2S1f. The extension-shape and profile-parameter work
(V2S1a / V2S1b) lands independently of L2d; the audit (V2S1d)
adds value immediately to both `/shepard/api/...` and the
forthcoming `/v2/...` surface; the flat-path work (V2S1c) waits
for L2d to land first.

**Honest estimate.** ≈ 4-6 engineer-weeks across the six
slices, dominated by V2S1c (flat endpoint scaffolding — 34
resource classes, one PR each is the comfortable shape) and
V2S1d (the per-operation summary/description/example audit).
V2S1a-V2S1b-V2S1d are each 1-2 weeks; V2S1e is 1-2 weeks; V2S1f
is a half-week.

---

## 7. Compatibility

- **`/shepard/api/...` byte-frozen.** No breaking changes. Every
  current nested path keeps working with current wire shape.
- **`/v2/...` shipped additively.** Clients that don't know `/v2/`
  see no change. Clients that adopt `/v2/` get the flat paths,
  the `?profile=` parameter, and the MCP-friendly OpenAPI
  metadata.
- **Deprecation window** for legacy nested single-entity paths
  follows `aidocs/34` / L2e — long horizon (≥ 2 minor releases
  after V2S1c lands; admin-comfortable). V2S1f's `Sunset` header
  is the formal mechanism.

For an admin upgrading from upstream:

- Nothing breaks. The shape of `/shepard/api/...` is untouched.
- The `/v2/...` surface is an opt-in addition the admin doesn't
  have to think about until they want it.
- A client that adopts `/v2/...` gets the simpler shape, the
  profile parameter, and the MCP-readiness — but no migration
  pressure forces it to.

---

## 8. Open questions

| # | Question | Recommendation |
|---|---|---|
| 1 | Does `?profile=` apply on `/shepard/api/...` too, or only `/v2/`? | **Only `/v2/`.** The upstream surface is byte-frozen; adding a query param that changes the response shape is a wire change even if it's opt-in. Restricts the profile work to one surface. |
| 2 | Are we OK breaking REST-orthodoxy by flat-pathing single-entity endpoints? | **Yes** — per the user question. The flattening is single-entity only; bulk listings stay nested. The cost is paid by purists; the benefit is paid out to the casual user and the MCP integration. |
| 3 | Should `x-mcp-side-effects: admin` imply `@RolesAllowed("instance-admin")`? | **Yes.** Add an ArchUnit rule (`backend/src/test/java/.../archunit/McpExtensionRulesTest.java`): every operation with `x-mcp-side-effects: admin` has the role guard, and vice versa. Two-way invariant; CI-enforced. |
| 4 | Bulk-operation endpoints — `POST /v2/dataobjects:batchUpdate` (Google-style colon segments)? | **Defer.** The bulk-write pattern needs a separate design (rate-limit shape, partial-failure response, async-job interplay with `aidocs/32`). Out of scope for V2S1; pencilled in as V2S2. |
| 5 | Versions endpoints — `GET /v2/versions/{versionUid}` flat, or `GET /v2/collections/{appId}/versions/{versionUid}` nested? | **Flat.** A `versionUid` is already a UUID; the parent path is decorative. The body still includes `parentCollection.appId` for navigation. |
| 6 | Should `profile=relations` emit related-entity `appId`s as **strings** or as **`{ appId, kind }` objects**? | Strings for v1; `kind` is derivable from the property name on the parent (`fileReferences: [...]` → kind = `file-reference`). If a real ambiguity case shows up, revisit. |
| 7 | Does the MCP-server generator emit TypeScript / Java MCP servers too, or only Python? | Python only for v1. The MCP ecosystem is Python-leading; TS / Java follow if demand shows up. The generator's design keeps `--language=python` as the only flag in v1. |
| 8 | What about WebSocket / SSE endpoints (`aidocs/28 §SSE`) — how do they fit the MCP shape? | Out of scope for V2S1. MCP doesn't have a clean "streaming tool" abstraction yet (the protocol is request/response shaped); SSE endpoints get `x-mcp-tool-name: <name>_subscribe` but the generator skips them. Revisit when MCP adds streaming. |

---

## 9. Cross-references

- **`aidocs/25`** — L2 chain. V2S1c is gated on L2d landing. The
  flat-path design assumes `appId` is already the public
  identifier; without L2d that's not true.
- **`aidocs/47`** — Plugin SPI + DX series. V2S1e (MCP-server
  generator) is the V2-surface adopter of DX3
  (`mvn shepard:scaffold-payload-kind` archetype style — same
  Maven-plugin shape).
- **`aidocs/51`** — Instance-admin role. The
  `x-mcp-side-effects: admin` extension ↔
  `@RolesAllowed("instance-admin")` two-way invariant is the
  glue (per §8 open question 3).
- **`aidocs/49`** — In-app user docs use the same OpenAPI source
  the MCP generator does; per-endpoint examples and descriptions
  serve both audiences.
- **`aidocs/16`** — V2S1 row added later (by the dispatcher) once
  this design is ready for dispatch.
- **`aidocs/23`** — The original API critique that listed
  redundant path-params as pain point. This design closes that
  thread for the `/v2/` surface.
- **`aidocs/27`** — Convenience clients (P16). The flat
  single-entity paths + the `?profile=` parameter directly
  simplify the `shepard-py` / `shepard-ts` wrappers — `client
  .get(appId, profile="metadata")` is one call, one arg, one
  shape.
- **`aidocs/33`** — Frontend workflow analysis. The flat paths
  unblock W2 / W5 / W11 deep-linking cases.
- **`aidocs/H4`** — RFC 7807 problem+json. The `?profile=`
  unknown-name error rides this surface.

---

## 10. What this is NOT

- **Not** a rewrite of `/shepard/api/...`. The upstream surface
  stays byte-frozen.
- **Not** GraphQL. `?profile=custom&fields=...` is reserved as a
  future shape; v1 ships three named profiles only.
- **Not** gRPC. The transport stays HTTP + JSON.
- **Not** promising an MCP server on shepard startup. The design
  makes the OpenAPI **agent-ready** for later MCP-tool
  generation; the generator (V2S1e) emits a stub Python MCP
  server out-of-band, not in-process. Whether to ship a
  shepard-mcp-server alongside the backend is a separate
  decision (one option: a `shepard-mcp` Docker image in the same
  compose stack, sidecar shape).
- **Not** a way to break REST contracts in `/shepard/api/...`.
  The flat-path simplification applies to `/v2/...` only.
- **Not** an HATEOAS / HAL-Forms / JSON-LD-on-by-default move.
  The body's `parentCollection: { appId, name }` is a navigation
  hint, not a link relation with a `_links` envelope. We can add
  HAL later if a use case demands; v1 stays JSON-plain.
