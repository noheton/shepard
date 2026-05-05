# API Critique — Usability, Redundancies, Paradigms, Client Generation

**Snapshot date:** 2026-05-05.
**Branch:** `worktree-agent-ab4459e2692c54f69` (off `develop`).
**Scope.** A focused critique of the shepard REST surface and the
auto-generated clients underneath it. Triggered by the maintainer's
verbatim complaint in `aidocs/input/input_raw.md:4-5`: *"rest api
improvements? feels clunky. maybe even other apis that are faster or
easier to integrate eg timeseries to excel via sql"*.

This document is companion to `13-search-improvements.md` (which
unifies the search slice), `18-pagination-inventory.md` (which already
inventoried the 38 paginating endpoints), and `19-architecture-feedback.md`
(cross-cutting concerns). It does **not** repeat them; it cites and
extends. Where this doc proposes work, it maps to a backlog ID — either
an existing item in `16-dispatcher-backlog.md` (A2, P4, P4b, L6,
A4d, H4, P3) or a new ID introduced here.

**Status legend.** Same as the perf/search docs: HIGH / MED / LOW /
ARCH / DONE.

---

## 1. Executive summary

The REST surface is functional, broadly RESTful, and discoverable
through Swagger UI — but it has accumulated the asymmetries you get
when a surface grows organically across five storage backends and a
decade of sprints. The shape is clunky in three orthogonal ways: the
**resource model leaks the storage layout** (5-tuple addressing for
timeseries, OID for files, `(collectionId, dataObjectId, referenceId)`
triples for references), the **cross-cutting concerns are
non-uniform** across endpoints (pagination, error envelope, sort,
ordering of permissions endpoints), and the **client SDK ergonomics
are dominated by generator-induced boilerplate** rather than the
domain (Python prelude in `input_raw.md:7-22`).

The three biggest wins, ranked: (1) introduce a SQL-over-HTTP /
Arrow Flight slice for bulk timeseries reads — this directly answers
"timeseries to Excel"; (2) collapse the five search endpoints behind
the unified `POST /search/v2` design in `aidocs/13`; (3) move blob
payloads (FileContainer, StructuredDataContainer) to S3-presigned URLs
for upload/download. The one thing the team should *not* do, despite
the temptation in `input_raw.md:695`: introduce GraphQL across the
whole API. It does not solve the "clunky" problem and adds a second
schema source of truth on top of OpenAPI.

---

## 2. Where the API is clunky

This section enumerates concrete pain points with code citations.
Where a backlog item already covers a fix, that ID is referenced.

### 2.1 Resource model leaks the storage layout

The most visible cost. Three examples, all on hot paths:

- **Timeseries 5-tuple addressing.**
  `TimeseriesRest.getTimeseries`
  (`backend/src/main/java/de/dlr/shepard/data/timeseries/endpoints/TimeseriesRest.java:336-348`)
  takes `measurement`, `device`, `location`, `symbolicName`, `field`
  *all required* to fetch a single timeseries payload. The same
  surface accepts `timeseriesId` on
  `getTimeseriesById:304-307` because the payload column has both a
  numeric PK and the 5-tuple — but `payload` and `export` still
  insist on the tuple. Clients hold five strings to identify one
  series. This is the same pain `13-search-improvements.md §2.3`
  and `12-timescaledb-performance-analysis.md §11.B` are already
  aiming to fix; the *API* fix is to deprecate the tuple-keyed
  endpoint variants. Tracked under `12 §11.B` and `P3`.

- **File OIDs in path.**
  `FileRest.getFileById`
  (`backend/src/main/java/de/dlr/shepard/data/file/endpoints/FileRest.java:181-202`)
  routes by `(fileContainerId, oid)` where `oid` is a Mongo
  GridFS object id — a storage detail leaking into the public
  surface. The OID has no meaning to a client; if shepard ever
  swaps the file store (e.g. to MinIO/S3 — see `input_raw.md:703`
  and §4.2 below) every URL and every saved client breaks.

- **Reference triples in path.**
  Six per-kind reference rests
  (`FileReferenceRest`, `TimeseriesReferenceRest`,
  `StructuredDataReferenceRest`, `URIReferenceRest`,
  `DataObjectReferenceRest`, `CollectionReferenceRest`,
  `SpatialDataReferenceRest` —
  `backend/src/main/java/de/dlr/shepard/context/references/`)
  each carry the same `(collectionId, dataObjectId, referenceId)`
  triple in path and parameter shape. The duplication is the cost
  of generating per-kind endpoints rather than letting one
  `/references/{id}` route by polymorphic dispatch. Backlog: A2.

### 2.2 Path / query-param naming is non-uniform

Spot checking the three target rests:

- `TimeseriesRest` uses path constants that compose into
  `/timeseries-containers/{containerId}/timeseries/{timeseriesId}`
  but the *query parameter* names mix camelCase
  (`symbolicName`, `groupBy`, `fillOption`) with one-word lowercase
  (`measurement`, `device`, `location`, `field`, `start`, `end`)
  in the same handler signature
  (`TimeseriesRest.java:336-348`).
- `FileRest` uses `payload` as a *path segment*
  (`FileRest.java:163`) while `TimeseriesRest` uses `payload` as a
  *path segment* too (`TimeseriesRest.java:181`) — consistent — but
  `StructuredDataRest` mixes `payload`/`payloads` between operations.
- Search endpoints use `page` + `size` on `/search/containers`
  (`SearchRest.java:107-108`), but the legacy `/collections` endpoint
  uses the same names with subtly different semantics (1-based
  vs 0-based; see `SearchRest.java:96` "Pagination starts at 0"
  comment, contradicting the legacy direct CollectionRest list).
  The full inventory is in `aidocs/18-pagination-inventory.md` —
  38 endpoints, four distinct conventions. Backlog: L6.

### 2.3 Endpoint-count smell, especially on the `*Rest` carrying
storage-shaped routes

The repo has **153 REST methods** across `*Rest.java` classes
(`grep -rE '^\s*@(GET|POST|PUT|DELETE|PATCH)' backend/src/main/java
--include='*Rest.java' | wc -l`). Top three by class:

| Rest class | Methods |
|---|---|
| `TimeseriesRest.java` | 14 |
| `StructuredDataRest.java` | 11 |
| `FileRest.java` | 11 |

Counting by *related package* rather than class — the timeseries
slice spreads across `TimeseriesRest`, `AnnotatableTimeseriesRest`,
`TimeseriesReferenceRest`, `TimeseriesReferenceMetricsRest`. That is
**26 timeseries-related endpoints** when we count
`grep ... | grep -i timeseries`. Same domain, four entry points,
overlapping concerns (a timeseries reference is just a pointer to
a timeseries payload; their permission models, list shapes, and
search filters diverge). Backlog: A2.

### 2.4 Five separate search endpoints

`SearchRest.java:65-204` exposes `/search`, `/search/collections`,
`/search/containers`, `/search/users`, `/search/usergroups`. Each
takes a different body type (`SearchBody`, `CollectionSearchBody`,
`ContainerSearchBody`, `UserSearchBody`, `UserGroupSearchBody`),
returns a different result envelope, and has different pagination
semantics. Already analysed in `13-search-improvements.md §1.1`,
fix proposed in §2 of that doc. Listed here only for completeness
of the clunkiness inventory.

### 2.5 Permission semantics leak through path-segment switching

`PermissionsService.isAllowed`
(`backend/src/main/java/de/dlr/shepard/auth/permission/services/
PermissionsService.java:198-243`) decides authorisation by
*pattern-matching the path*: a switch on the first path segment
(`temp/migrations`, `lab-journal-entries`, `users`, `search`),
plus a numeric-ID check on the second segment, plus a special-case
exception for the search-by-kind endpoints (lines 232-240). When
the path doesn't match any branch, the method falls through to
`return false` — meaning a new endpoint is implicitly *denied*
unless its path matches an existing branch. This is structurally
fragile (any path renaming silently breaks auth), and it's the
reason `13-search-improvements.md` flags the search-paths
exception pattern as needing to die. Backlog: new item **P5**
(see §6).

### 2.6 Error responses are non-RFC-7807

`ApiError.java:1-11` is the error envelope:

```java
@Value
public class ApiError {
  private final int status;
  private final String exception;
  private final String message;
}
```

Three custom fields, no `type` URI, no `instance`, no
`detail` — and `exception` exposes a Java class name, which is
both an information leak and a coupling to the implementation
language. Standards-aligned shape is RFC 7807
(`application/problem+json`), already on the backlog as **H4**
in `16-dispatcher-backlog.md`. The fix is small and well-scoped
(one `ExceptionMapper` rewrite plus a media-type switch).

### 2.7 Pagination inconsistency

Already inventoried — see `aidocs/18-pagination-inventory.md`.
Headline: 38 endpoints with at least four distinct contracts
(offset+size, 1-based pageNumber+pageSize, no pagination at all,
`Range` header on the streaming endpoints). Cursor pagination
proposed for the unified search in `13 §2.6` and would be the
right default for the rest. Backlog: L6.

### 2.8 Auth header redundancy: `X-API-KEY` + `Authorization`

`JWTFilter.java:107` reads `X-API-KEY` after also reading the
`Authorization: Bearer …` header (line 114 logs both). Two equal-
priority auth schemes living in two header slots is a frequent
client-confusion source — the Python prelude in
`input_raw.md:14` constructs a `Configuration(... api_key={"apikey":
APIKEY})` mapping precisely because the SDK doesn't know which
slot to pick. RFC 6750 prescribes the bearer slot for API tokens;
moving API keys behind `Authorization: Bearer` is a one-line
filter change with a deprecation window. Backlog: new item
**P6** (see §6).

### 2.9 Query-by-example fragmentation

The repo doesn't have explicit `/byNames` / `/byIds` /
`/byNeo4jIds` endpoints (good — we checked with `grep -rn` and
they are absent), but it has the *equivalent* fragmentation in
practice:

- `getTimeseriesById:304-307` (single, by numeric id)
- `getTimeseriesOfContainer:266-273` (list, with five optional
  query-param filters, all string-equals)
- `getTimeseries:336-348` (single, by 5-tuple, required)
- `getTimeseriesAvailable:229` (list, no filter)

Four read paths into the same Postgres view, each with a
different filter contract. The pattern repeats on `FileRest`
(`getAllFiles:176`, `getFileById:181`,
`uploadFile:244`, `deleteFile:212`) and on the `*ReferenceRest`
classes. This is what the maintainer's "clunky" comment is
describing concretely: the SDK user has to *remember* which
read path applies to which key shape.

### 2.10 Python boilerplate cost

The `input_raw.md:1-30` snippet shows the cost the maintainer
sees daily:

```python
from shepard_client.api_client import ApiClient
from shepard_client.configuration import Configuration
HOST = "..."; APIKEY = "..."
conf = Configuration(host=HOST, api_key={"apikey": APIKEY})
client = ApiClient(configuration=conf)
from shepard_client.api.collection_api import CollectionApi
from shepard_client.models.collection import Collection
collection_api = CollectionApi(client)
collections = collection_api.get_all_collections()
```

Eight lines of imports + two configuration objects + per-tag API
classes (`CollectionApi`, `DataObjectApi`) before a single domain
call. None of this is shepard-specific — it is the
default `openapi-generator -g python` ergonomic. A 30-line
convenience module (`shepard_py.connect(host, apikey) -> Client`)
collapses lines 1-22 to two. Discussed in §5.6.

### 2.11 The `IO` suffix on client model names

`@Schema(name = "DataObject")` on
`DataObjectIO.java:13` renames the class for OpenAPI emission.
Not every IO class carries the override (60-odd files); a few
slip through and clients see the `IO` suffix. Lint-fixable in
CI (`grep -L '@Schema(name'` over IO files).

---

## 3. Redundancies — endpoint groups that should collapse

| Group | Endpoints today | Replace with | Backlog |
|---|---|---|---|
| Per-kind reference rests | 7 classes (`FileReferenceRest`, `TimeseriesReferenceRest`, `StructuredDataReferenceRest`, `URIReferenceRest`, `DataObjectReferenceRest`, `CollectionReferenceRest`, `SpatialDataReferenceRest`) | One `BasicReferenceRest` with a `kind` field on the body; per-kind URL templates kept as 301 redirects for one minor version | A2 |
| Five search endpoints | `SearchRest.java:65-204` | `POST /search/v2` per `aidocs/13 §2` | new **P7** |
| Per-kind semantic-annotation rests | `DataObjectSemanticAnnotationRest`, `CollectionSemanticAnnotationRest`, `BasicReferenceSemanticAnnotationRest`, `AnnotatableTimeseriesRest` | One `/annotations` endpoint that accepts a polymorphic subject (entity-IRI or kind+id) — depends on `aidocs/14` model unification | new **P8** |
| Two timeseries read paths | `getTimeseriesById` (numeric id) + `getTimeseries` (5-tuple) | Drop the 5-tuple after `12 §11.B` ID alignment lands | P3 |
| Permissions endpoints (per-kind) | `*Rest`/`{id}/permissions` and `*Rest`/`{id}/roles` repeated on Collection, Timeseries, File, StructuredData, Spatial containers | One `/entities/{kind}/{id}/permissions` (or a header-driven entity reference) | new **P9** |
| Subscribe endpoints (`SubscriptionRest`) | per-resource `@Subscribable` annotations turned into URL routes | A subscription is a *meta* concern; consolidate into one `POST /subscriptions {target: {kind, id}}` (largely already there — just stop annotating per-resource) | A2 |

---

## 4. Better-suited paradigms (per slice)

Honest, paradigm-by-slice. *Not* "use GraphQL everywhere".

### 4.1 Bulk timeseries reads → SQL-over-HTTP **or** Apache Arrow Flight

This is the most direct answer to the maintainer's "timeseries to
Excel via SQL" prompt (`input_raw.md:5-8`).

**The core problem.** `getTimeseries` and `exportTimeseries`
(`TimeseriesRest.java:336-423`) build a `TimeseriesWithDataPoints`
in heap and serialise as JSON or CSV. Beyond the heap-blowup risk
(`12 §11.A`), the surface is locked: the only aggregations are
`AggregateFunction` enum values, `groupBy` is a single integer
millisecond bucket, and there is no `JOIN` across timeseries. A
power user wanting "average voltage per minute over the last 8h
joined with average current" is forced into N round-trips and
client-side merging.

**The proposal.** Two complementary endpoints, behind the same
authorisation model:

1. **`POST /sql/timeseries`** (PostgREST style, but *curated*).
   Accepts a `SELECT` against a curated set of views over the
   timeseries hypertable (`shepard_public.timeseries`,
   `shepard_public.annotated_timeseries`,
   `shepard_public.ts_minute_bucketed`). Roles + `statement_timeout`
   + row cap enforced server-side. Already proposed inline in
   `13 §5` as the `raw.sql` operator on the unified search; this
   §4.1 endpoint is the *standalone* surface for the
   "timeseries-to-Excel" workflow — it can be hit from Excel's
   `WEBSERVICE` / Power Query directly without going through
   the search envelope. Excel + Power Query natively reads CSV
   over HTTP; serving `text/csv` from this route gives "paste
   the URL into Excel" ergonomics for free.

2. **`POST /flight/timeseries`** (Apache Arrow Flight SQL).
   Optional, gated. Same SELECT, but the wire format is Arrow
   IPC. Pandas/Polars/DuckDB clients read Arrow zero-copy;
   it is the right surface for analytical notebooks and
   roughly 10x faster than JSON for 1M-point reads. Quarkus has
   no first-class Arrow Flight extension yet, so the cost is
   non-trivial — *defer* until #1 is shipped and the demand is
   measured. (DuckDB-over-HTTP is a lower-overhead alternative:
   expose the curated views through a DuckDB process with the
   `httpfs` and `postgres_scanner` extensions; clients then
   write `SELECT * FROM postgres_scan('host', 'db', 'view')`.
   That is a cleaner story than wrapping ourselves.)

Backlog: new item **P10** (SQL-over-HTTP, M); new item **P11**
(Arrow Flight, L, ARCH-deferred).

### 4.2 Blob payloads → S3-presigned URLs

`FileContainer.payload` (`FileRest.java:181-244`) and
`StructuredDataContainer.payload` (`StructuredDataRest`) currently
stream binary content *through* the Quarkus app heap. The roadmap
already mentions S3/MinIO migration for files
(`input_raw.md:703`).

**The proposal.** Once an object store is wired:

- `POST /file-containers/{id}/payload/upload-url` returns a
  presigned PUT URL + expected SHA-256 + lifetime.
- `GET /file-containers/{id}/payload/{oid}/download-url` returns a
  presigned GET URL.
- Existing `POST /file-containers/{id}/payload` and
  `GET /file-containers/{id}/payload/{oid}` proxy modes stay alive
  for tiny payloads / clients that can't follow redirects, behind
  a 30-day deprecation banner.

The Quarkus process handles auth + metadata + presigning; bytes
flow client ↔ object store directly. Same pattern works for
StructuredDataContainer payloads. The OID-as-path-component pain
(§2.1) is replaced by a server-issued opaque URL.

Backlog: new item **P12** (S3 presigned, M).

### 4.3 Search & navigation → unify under `aidocs/13`; do not GraphQL

`13-search-improvements.md` already proposes one `POST /search/v2`
with a typed predicate body, cursor pagination, and `include`-
expansions. That envelope is sufficient for the "find collections
+ their containers + their annotations matching X" pattern that
today takes three calls and a client-side merge.

**Why not GraphQL.** GraphQL is *pull-shaped* (`13 §1.1`'s
multi-call example) but the cost is non-trivial: a second schema
source of truth (`schema.graphql` + `*.graphqls` resolver wiring),
N+1 query risk on top of Neo4j, dataloader plumbing, and a
permissions checker that has to descend the resolution tree.
shepard's current authorisation is path-shaped
(`PermissionsService.isAllowed:198-243`), and that approach
breaks in GraphQL because there is no path. A predicate-JSON
body keyed by entity kind handles the same multi-kind read with a
fraction of the surface area. Suggested only as a **read-only,
optional /graphql over Neo4j** if and when the unified search
envelope hits a wall on a deep traversal pattern (e.g. "all
DataObjects descended from Collection X annotated with
`dcterms:creator`") — and even then it should be an *additional*
endpoint, not a replacement.

Backlog: P7 (unification), no GraphQL backlog item.

### 4.4 Subscriptions / change-feeds → SSE for tail-following

`SubscriptionRest` + `@Subscribable` produce *outbound webhooks*
(security finding C4 — see `aidocs/01-repo-overview.md:75`). For
the inbound shape ("show me everything that happens to this
collection in the next 60 minutes"), the right primitive is
**Server-Sent Events** (`text/event-stream`):

- `GET /collections/{id}/events` returns an SSE stream emitting
  `entity-created`, `entity-updated`, `permission-changed`,
  `annotation-added` events.
- Browser-native (`EventSource`), Python via `httpx-sse`, no
  WebSocket framing complexity, traverses HTTP/2 cleanly.
- WebSockets only if a true bi-directional channel emerges
  (e.g. an interactive labeling UI) — none today.

Quarkus SSE support is built in (`@Produces(MediaType.SERVER_SENT_EVENTS)`).
Backlog: new item **P13** (SSE feed, M).

### 4.5 Bulk writes / imports → multipart / NDJSON streaming

`importTimeseries` (`TimeseriesRest.java:425-440`) takes
`MultipartBodyFileUpload` for the import path — fine for one CSV.
For *streaming* imports (e.g. continuous lab measurements being
forwarded), the right shape is **NDJSON over `application/x-ndjson`**:
one JSON object per line, server flushes per line, no body-size
ceiling. This pairs naturally with the S3-presigned upload path
in §4.2 for very large imports (let the client upload to S3 and
notify shepard). Backlog: new item **P14** (NDJSON ingest, S).

### 4.6 Hypermedia (HAL / Siren) — argue against

The cost of HAL/Siren is per-endpoint envelope wrapping; the
benefit is "discoverability" — a client follows links rather
than knowing URLs. shepard already serves OpenAPI 3.0 at
`/shepard/doc/openapi.json` and Swagger UI; the OpenAPI spec
*is* the discoverability mechanism for typed clients. Adding
HAL would inflate every payload (extra `_links`, `_embedded`)
without changing what real-world clients actually do (read the
OpenAPI, generate, call). Skip.

### 4.7 GraphQL — argue against wholesale (see §4.3)

### 4.8 gRPC — out of scope, document why

shepard's clients are Python/TS/Java, all happily speaking JSON.
gRPC's wins are wire efficiency (Protobuf) and cross-language
typed streaming. Wire efficiency is solved by §4.1 (Arrow);
streaming is solved by §4.4 (SSE) and §4.5 (NDJSON). The cost
of gRPC is a second deployment artefact (gateway / Envoy), a
second schema source of truth (`*.proto`), and broken browser
support without `grpc-web`. Not worth it for shepard's caller
mix.

### 4.9 Async / pub-sub — Quarkus reactive replaces blocking

A handful of read endpoints today block on a Postgres or Neo4j
round-trip while holding a worker thread. `getTimeseriesAvailable`
(`TimeseriesRest.java:266-273`) is one — it filters in-memory
after fetching. Migrating these to Mutiny (`Uni<Response>`) on
Quarkus reactive frees worker threads and enables backpressure.
This is structural; covered by the existing A2 epic in
`16-dispatcher-backlog.md` — call out timeseries reads as the
first slice.

### 4.10 OpenAPI 3.1

shepard emits OpenAPI 3.0 (`backend/target/openapi/openapi.json`).
3.1 aligns with JSON Schema 2020-12 (`nullable` becomes `type:
[..., "null"]`, `examples` instead of `example`, etc.) and
removes the `nullable` post-processing step in
`scripts/shepard_scripts/scripts/patch_openapi_for_python.py:1-37`.
The MicroProfile OpenAPI annotation pipeline (used by Quarkus)
emits 3.0; the upgrade has to wait for `smallrye-open-api` 4.x
and downstream generator support. Backlog: new item **P15**
(OpenAPI 3.1 migration, M, blocked on tooling).

---

## 5. Client-generation story

### 5.1 Current pipeline

| Client | Image | Generator template | Spec input | Post-processing |
|---|---|---|---|---|
| Python | `openapitools/openapi-generator-cli:v7.12.0` | `python` | `openapi_no_required.json` (post-patched) | `scripts/.../patch_openapi_for_python.py` strips required for read-only/nullable fields |
| Java | `openapitools/openapi-generator-cli:v7.16.0` | `java` | `openapi.json` (raw) | None |
| TypeScript | `openapitools/openapi-generator-cli:v7.12.0` | `typescript-fetch` | `openapi.json` (raw) | None |

References: `.gitlab/ci/clients/{python,java,typescript}.gitlab-ci.yml`,
`.gitlab/ci/jobs/unrequire-attributes-for-python.yml`,
`scripts/shepard_scripts/scripts/patch_openapi_for_python.py:1-37`,
`clients/java/config.yaml`, `openapitools.json`.

Three immediate observations:

- Generator versions drift across languages (7.12.0 vs 7.16.0).
  Pin once (the `openapitools.json` only pins one
  `version: 7.8.0` for the local CLI invocation, which is *not*
  what CI uses).
- Python needs a custom monkey-patch on the OpenAPI before the
  generator runs — confirmation that the contract isn't quite
  clean for the Python generator's `required` semantics.
- Java client config (`clients/java/config.yaml`) sets
  `disallowAdditionalPropertiesIfNotPresent: false`. This is a
  workaround for OpenAPI 3.0's ambiguity around
  `additionalProperties`; it permanently widens the generated
  models. Worth tightening with a *positive* declaration on each
  schema.

### 5.2 Boilerplate cost — quantified

`input_raw.md:1-22` is 22 lines of which 14 are config / imports
that no caller cares about. The same call in a "convenience-layer"
shape would be:

```python
import shepard_py
sh = shepard_py.connect(host=HOST, api_key=APIKEY)
print(sh.collections.list())
print(sh.collections.create(name="MyFirstCollection",
                            description="...",
                            attributes={...}))
```

Three lines, no per-tag API class. The convenience layer is
trivial (~30 LoC: thin wrapper around the generated `ApiClient`,
attribute-style proxy `sh.collections` → `CollectionApi(self.client)`,
keyword-only `create` that builds `Collection(...)` internally).
Discussed as backlog **P16** in §6.

### 5.3 Quality issues in the generated code

- **Enums-as-strings.** TS `AggregateFunction` is a string-literal
  union; Python is a `str, Enum`. Ergonomic, but adding a value
  is a silent breaking change for clients pinning generator
  output.
- **Optional vs nullable.** OpenAPI 3.0 doesn't distinguish
  "absent" from "present null"; the
  `patch_openapi_for_python.py:7-30` script is the band-aid.
  OpenAPI 3.1 (§4.10) fixes this.
- **Naming churn.** See §2.11. Lint-fixable.
- **Tag grouping drift.** Operations are grouped into per-tag
  API classes by the generator
  (`CollectionApi`, `DataObjectApi`, `TimeseriesContainerApi`)
  via `@Tag(name = ...)` on each handler. A handful are mistagged.
  Audit-fixable.
- **Deprecation propagation.** `@Deprecated` on a Java handler
  doesn't always set `deprecated: true` in the OpenAPI doc.
  Test in CI.

### 5.4 Alternative generators — one sentence each

- **`openapitools/openapi-generator-cli` (today).** Most
  templates (50+), most lock-in: hard to escape per-template
  quirks once they ship in published packages. Stay if Java is
  the priority client.
- **`oapi-codegen`.** Go-only. Out of scope for shepard's
  caller mix unless a Go client appears.
- **Stainless** (commercial; <https://stainless.com>). Generates
  high-quality Python/TS/Go/Java SDKs with idiomatic ergonomics
  (auto-pagination iterators, retry logic, structured errors).
  Closed-source, paid (per-spec subscription), and *might* have
  on-premise deployment options for DLR's air-gapped concerns —
  worth contacting them. Major win on the boilerplate axis (§5.2).
- **Microsoft Kiota.** Open-source, supports Python/TS/Java/Go/C#,
  generates "fluent" path-builder clients
  (`client.collections.byCollectionId('123').get()`). One of the
  better stories for a hierarchical resource graph like shepard's.
  Strong OpenAPI 3.1 support. Worth a 1-day prototype against the
  current spec.
- **Smithy.** AWS's IDL — a *replacement* for OpenAPI as the
  source of truth, with multi-protocol code generation
  (REST/JSON, gRPC, Arrow Flight). Heavy migration; only worth
  it if the team also wants to grow §4.1 and §4.4.
- **OpenRPC.** Targets JSON-RPC, not REST. Out of scope.
- **tRPC.** End-to-end TS only, expects the server *and* client
  in TS. shepard's server is Java. Skip.

### 5.5 Recommendation — generator and schema source

**Recommendation: stay** with `openapi-generator-cli`, but
(a) pin one version across all three languages, (b) prototype
**Microsoft Kiota** in parallel for one minor version and
benchmark generated-code ergonomics + LoC + bundle size, and
(c) treat the convenience layer (§5.2) as the *real* fix for
boilerplate — it is a 30-LoC patch, not a generator switch.

**Recommendation: schema source of truth — stay with
annotation-generated OpenAPI (status quo), upgrade to OpenAPI
3.1 when `smallrye-open-api 4.x` is stable.** Reasoning:

- **TypeSpec** (Microsoft) is excellent at describing typed REST
  surfaces and emits OpenAPI 3.1 cleanly. *But* the source of
  truth would move from Java annotations to a separate
  `*.tsp` file, and the Quarkus handler would need to re-derive
  the contract — a ceremony that breaks the "annotate the
  handler, ship the spec" workflow shepard has.
- **Smithy** has the same cost plus a heavier syntax. Its win is
  multi-protocol; until §4.1 actually ships, we don't need it.
- **Status quo** has one cost: handler-level OpenAPI annotations
  drift from the actual handler signature
  (e.g. `getTimeseriesOfContainer:260-265` declares
  `@Parameter(name = ...)` for each query param manually,
  duplicating the `@QueryParam` declarations). A switch to
  OpenAPI 3.1 plus better Quarkus-side annotation hygiene closes
  the gap without changing the source.

If the team eventually wants multi-protocol emission (REST + Arrow
Flight + gRPC), revisit Smithy. Until then, it's overkill.

### 5.6 Convenience layer per language — `shepard-py`, `shepard-ts`

The *real* lever on the boilerplate complaint is the convenience
wrapper. Concrete proposal:

- `shepard-py` package: wraps the generated `shepard_client` with
  a `Client` that holds host + auth, exposes domain-shaped
  attributes (`sh.collections`, `sh.timeseries`, `sh.search`),
  ships pagination iterators, and adds three workflow helpers
  (`sh.timeseries.to_pandas(timeseries_id)`,
  `sh.timeseries.to_excel(...)`, `sh.export.ro_crate(collection_id)`).
- ~150 LoC, no new dependencies.
- Maintained in the same repo as the generated client; published
  to the same PyPI index.
- Cost: maintenance burden when API surface changes (mitigated
  if convenience methods only call generated endpoints).
- Same shape for `shepard-ts`.

Backlog: **P16** (S per-language).

---

## 6. Recommendations — sized

Recommendations introduced in this doc plus pointers back to
existing backlog items they intersect with.

| ID | Pitch | Size | Backlog item / epic |
|---|---|---|---|
| P5 | Replace path-segment switching in `PermissionsService.isAllowed` with annotation-based authz on each handler | M | new |
| P6 | Fold `X-API-KEY` into `Authorization: Bearer`, deprecate the second header | S | new |
| P7 | Ship the unified `POST /search/v2` from `aidocs/13 §2`; deprecate the five legacy search routes | L | new (epic in `aidocs/20`) |
| P8 | Polymorphic `/annotations` endpoint replacing the four per-kind annotation rests | M | depends on `aidocs/14` model unification |
| P9 | Single `/entities/{kind}/{id}/permissions` route replacing the per-container permissions endpoints | M | new (overlaps A2) |
| P10 | `POST /sql/timeseries` curated SQL-over-HTTP for bulk reads — answers "timeseries to Excel" | M | new |
| P11 | Apache Arrow Flight / DuckDB read endpoint for analytical workloads | L (ARCH) | new, deferred |
| P12 | S3-presigned URLs for File and StructuredData payloads | M | dovetails with `input_raw.md:703` |
| P13 | SSE change-feed (`GET /collections/{id}/events`) | M | new |
| P14 | NDJSON streaming ingest for high-throughput timeseries imports | S | new |
| P15 | Migrate spec to OpenAPI 3.1 once `smallrye-open-api 4.x` is stable | M | blocked on tooling |
| P16 | `shepard-py` and `shepard-ts` convenience layers (3-line "hello world") | S per language | new |
| P17 | Pin generator version across languages, add Kiota PoC | S | dovetails with P4b |
| P17b | CI lint: every IO class has `@Schema(name=…)` | XS | dovetails with P4b |
| P18 | RFC 7807 error envelope (`application/problem+json`) | S | matches H4 |
| P19 | Cursor pagination on the unified search; offset elsewhere stays for now | M (in P7) | dovetails with L6 |
| P20 | Reactive (Mutiny) migration for the timeseries read path as the first slice | M | dovetails with A2 |

P5, P6, P7, P10, P12, P16, P18 are the *minimum-viable* clunkiness fix —
they address §2.1, §2.5, §2.6, §2.8, the timeseries-to-Excel prompt,
and the Python boilerplate, in roughly two sprints' worth of work.

---

## 7. Things to deliberately *not* do

1. **Don't introduce GraphQL across the API.** The pull-shape
   problem it solves is solved by the unified-search envelope
   (`aidocs/13 §2`) for less effort and without a second schema
   source of truth (§4.3). Read the `input_raw.md:695` GraphQL
   note as a *symptom* of the search fragmentation, not a
   prescription.
2. **Don't rewrite to gRPC.** Wire efficiency is the only real
   gain, and §4.1 (Arrow) plus §4.5 (NDJSON) cover the cases
   that actually matter (§4.8).
3. **Don't switch generators wholesale before fixing the schema
   hygiene.** Most "openapi-generator is bad" complaints in
   `input_raw.md:1-30` style are actually OpenAPI-3.0 quirks
   (`patch_openapi_for_python.py`) plus per-language post-
   processing. Fix the schema first; switch only if the
   ergonomic gap is still wide (Kiota PoC, §5.5).
4. **Don't move to Smithy or TypeSpec preemptively.** Both add
   a build step and a parallel source of truth. Worth it only
   if multi-protocol emission becomes a goal — not today.
5. **Don't try to make the API 100% RESTful.** `POST /search/v2`
   with a body and `POST /sql/timeseries` are *not* REST in the
   purist sense; that's fine. Practicality > purism, especially
   on the analytical slice where REST has always been a poor
   fit.

---

## 8. Open questions for the maintainer

- **SQL-over-HTTP scope (§4.1).** Read-only views over the
  hypertable are clearly the safe surface — but the maintainer's
  `input_raw.md:5-8` note hints at "easier to integrate, e.g.
  timeseries to Excel". Is the priority "Excel can paste a URL"
  (favours `text/csv` + a small set of canned queries), or
  "data scientist runs arbitrary SELECTs" (favours full
  curated-SELECT capability)? They imply different sandbox
  designs.
- **S3/MinIO concurrency (§4.2).** `input_raw.md:703` says
  "in work externally". What's the timeline? Backlog P12 should
  align rather than fork.
- **OpenAPI 3.1 timing (§4.10).** Is `smallrye-open-api 4.x`
  on the Quarkus extension roadmap shepard tracks? The fix for
  the `patch_openapi_for_python.py` post-process step depends
  on it.
- **Convenience layers — `shepard-py` and `shepard-ts` (§5.6).**
  Owned by the shepard repo, or sister repos? Naming
  (`shepard_client` is already taken on PyPI for the generated
  client; `shepard` would be the convenience name) is the
  bikeshed-bound part of the decision.
- **Permissions on raw-SQL surface (§4.1).** `aidocs/13 §8` calls
  this out as an open question already; flagged here for the
  per-row enforcement design (PostgreSQL row-level security
  policies vs view-level filter columns).
- **GraphQL escape hatch (§4.3).** Even if we don't ship
  GraphQL today, is there a future scenario (deep Neo4j
  traversals, a third-party visualiser) where one read-only
  `/graphql` would unlock something the predicate JSON can't?

---

## 9. References

- Maintainer prompt: `aidocs/input/input_raw.md:1-22, 4-5, 5-8,
  695, 703`.
- Backlog: `aidocs/16-dispatcher-backlog.md` (A2, P3, P4, P4b,
  L6, A4d, H4).
- Pagination inventory: `aidocs/18-pagination-inventory.md` (38
  endpoints).
- Architecture feedback: `aidocs/19-architecture-feedback.md`
  (cross-cutting concerns).
- Search unification: `aidocs/13-search-improvements.md` (entire).
- Semantic model: `aidocs/14-semantic-improvements.md` §2.
- Performance: `aidocs/12-timescaledb-performance-analysis.md`
  §11.A, §11.B.
- Admin CLI ergonomics: `aidocs/22-admin-cli-draft.md`.
- Code: `TimeseriesRest.java` (14 methods, 5-tuple),
  `FileRest.java` (11, OID in path),
  `CollectionRest.java` (9), `SearchRest.java` (5+5+5),
  `PermissionsService.java:198-243` (path switch),
  `ApiError.java` (non-7807), `JWTFilter.java:107-114` (dual auth),
  `DataObjectIO.java:13` (`@Schema(name=...)` rename).
- Client generation: `openapitools.json`, `clients/java/config.yaml`,
  `.gitlab/ci/clients/{python,java,typescript}.gitlab-ci.yml`,
  `.gitlab/ci/jobs/unrequire-attributes-for-python.yml`,
  `scripts/shepard_scripts/scripts/patch_openapi_for_python.py:1-37`.
