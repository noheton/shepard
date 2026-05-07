# 28. Paradigms-and-Clients Synthesis — The Integrated Proposal

**Snapshot date:** 2026-05-05.

**Scope.** Synthesis of the per-slice paradigm choices in
`aidocs/23-api-critique.md` §4 and the client-generation review in
§5 into **one** coherent surface. Both sections are correct in
isolation; read independently they would push shepard toward five
new protocols and three convenience tiers. This document picks the
*minimum sufficient* subset and binds it to a single schema source,
a single generator pipeline, and one convenience wrapper per
language so the maintenance burden does not compound.

**Companion docs.** `aidocs/13-search-improvements.md` (the
unified `POST /search/v2` slice this synthesis adopts wholesale),
`aidocs/16-dispatcher-backlog.md` (P5 / P10 / P12 / P13 / P15 / P16
/ P17 / P17b / P18 / P21, plus F1 / H4 / A2 / A4 / C5),
`aidocs/20-epic-roadmap.md` (the two-track frame the rollout maps
into), `aidocs/23-api-critique.md` §4 / §5 / §6 / §7,
`aidocs/26-crud-consistency.md` (the inconsistencies the synthesis
must fix, not paint over), `aidocs/27-convenience-clients-design.md`
(the wrapper design this synthesis treats as the multiplexer).

This document does **not** redebate per-slice paradigm choices;
those are settled in `aidocs/23 §4`. It does not restate the
sized recommendation table in `aidocs/23 §6`. It cites both and
integrates.

**Status legend.** HIGH / MED / LOW / ARCH / DONE — same as the
companion docs.

---

## 1. Executive summary

shepard's users want five things — CRUD on collections, fast bulk
timeseries reads, large blob upload/download, change-feeds, and a
3-line "hello shepard" — and the per-slice paradigm review in
`aidocs/23 §4` answers each correctly. Adopted naively, those
answers compound to **five wire protocols, three generated clients,
and two parallel schema sources**. The synthesis: collapse to
**four user-facing surfaces** (REST core, a narrow read-only
SQL-over-HTTP side door, S3-presigned blob URLs, SSE change-feeds),
**one** schema source (annotation-generated OpenAPI 3.1 once
P15 unblocks), **one** pinned generator
(`openapi-generator-cli`, P17), and **one convenience wrapper per
language** (`shepard-py` / `shepard-ts`, P16) that hides the
multi-surface dispatch from researchers. The wrapper is the
multiplexer that buys back the maintainability budget — without
it, every surface leaks into user code.

The two sentences a reader leaves with: **(1) The clunkiness comes
from one side and the maintenance load from the other; the
convenience wrapper is what reconciles them. (2) Three new
endpoints (P10, P12, P13) and one schema-hygiene lint (P17b) are
sufficient; everything else is already in `aidocs/16`.**

---

## 2. The maintainability budget — the constraint that drives everything

Every choice that follows is filtered through what shepard's
maintainer can actually keep alive. Stated as four hard limits:

**2.1 Clients per language.** Today: one *generated* client per
language (Python / Java / TypeScript), three total — see
`aidocs/23 §5.1`'s pipeline table. Convenience wrappers per
`aidocs/27 §3,§8` add **exactly one** more per language. **Hard
ceiling: two artefacts per language**, one generated, one
hand-maintained. Anything else (e.g. a separate `arrow-flight`
client, a separate `presigned-uploader` client, a separate
`sse-listener` client) violates the budget by definition. The
wrapper absorbs them.

**2.2 Schema sources of truth.** Today: one — the MicroProfile
OpenAPI annotations on the JAX-RS handlers, emitted via
`smallrye-open-api` to `backend/target/openapi/openapi.json`
(`aidocs/23 §5.5`). **Hard ceiling: one.** Adding TypeSpec, Smithy,
`*.proto`, or a `*.graphqls` resolver tree would create a second
truth and a "which one is canonical?" review-time question. P15
(OpenAPI 3.0 → 3.1) stays inside the same pipeline; it does not
add a source. Every paradigm in §3 below must be expressible
inside the existing OpenAPI spec or it does not ship.

**2.3 Test runtime.** Today: `mvn test` boots Quarkus once and
exercises the JAX-RS surface end-to-end via REST-assured plus
JUnit. **Hard ceiling: every new paradigm's integration test must
run inside `mvn test`** (or the existing client-side `pytest` /
`vitest` jobs in `.gitlab/ci/clients/*`). If SSE, SQL-over-HTTP, or
S3 presigning need a separate test runner, harness, or sidecar
container that doesn't already exist in `docker-compose.yml`, the
proposal fails on this axis.

**2.4 Best-of-breed is not the goal; survivable-in-five-years is.**
The maintainer is one or two people. Choosing Apache Arrow Flight
"because it's faster" before P10 is in the field, or Smithy
"because it emits more protocols", or Stainless "because the
boilerplate is better" — all costs paid up front for benefits paid
once over five years. The wins land more reliably with the
*serviceable* tool (`openapi-generator-cli`, plain SSE on
`@Produces(MediaType.SERVER_SENT_EVENTS)`, parameterised SQL over
HTTP) than with the fashionable one. `aidocs/23 §7.4` makes the
same point; this synthesis treats it as a hard rule.

These four limits are the **maintainability budget**. Every choice
in §3–§4 cites the limit it stays under. The ledger in §6 sums
the residual cost across all four.

---

## 3. The four-surface picture (down from the eleven implicit today)

`aidocs/23 §4` discusses eleven candidate paradigms (4.1 through
4.10 plus the legacy REST baseline). Today the surface implicit in
shepard is approximately seven (REST CRUD + SSE-shaped polling +
multipart upload + CSV export + JSON streaming + Cypher-skin search
+ pseudo-references), all funneled through one OpenAPI document.
The synthesis is to expose **four** to users: one core and three
narrow side doors. Each absorbs one or more `aidocs/23 §4` slices.

### 3.1 Surface A — the REST core

The bulk of the API. Carries CRUD on `Collection`, `DataObject`,
`*Container`, `*Reference`, `SemanticAnnotation`, `User`,
`UserGroup`, `ApiKey`, `Subscription` (29 `*Rest.java` classes,
`aidocs/26` §0). After the planned cleanup:

- One verb per resource per CRUD operation, per `aidocs/26 §3`
  finding #2 (PATCH for partial, PUT for full replacement —
  **P21**).
- Declarative `@Authz(action, resource)` per
  `aidocs/16 F1 / P5` (canonical ID **F1**), replacing the path
  switch in `PermissionsService.java:198-243` flagged by
  `aidocs/23 §2.5`.
- RFC 7807 `application/problem+json` per `aidocs/16 H4 / P18`
  (canonical ID **H4**), replacing the
  `ApiError {status, exception, message}` shape at
  `ApiError.java:1-11`.
- Pagination: existing `?page&size` per `aidocs/18-pagination-inventory.md`
  on the 27 unpaginated list endpoints (**L6**); cursor only on
  `POST /search/v2` per `aidocs/13 §2.6` (**P19**, in **P7**).
- One generated client per language (`shepard-client` / `shepard-client-java` / `@dlr-shepard/shepard-client`).

**Slices of `aidocs/23 §4` absorbed:**
- §4.3 (search/navigation) — adopted via `POST /search/v2`
  (`aidocs/13 §2`, backlog **P7**); this stays inside Surface A
  because it's still REST + JSON.
- §4.5 (NDJSON ingest) — alternate `application/x-ndjson` content
  type on existing `POST /timeseriesContainers/{id}/payload`
  (**P14**); the URL doesn't change.
- §4.10 (OpenAPI 3.1) — schema migration only (**P15**); no new
  endpoint surface.

**Maintainability:** zero new wire protocols, all changes inside
the OpenAPI spec, integration tests stay in REST-assured.

### 3.2 Surface B — bulk-data side door

A *narrow*, **read-only** SQL-over-HTTP endpoint at
`POST /sql/timeseries` per `aidocs/16 P10`, carrying `aidocs/23
§4.1`. Returns `text/csv` by default, `application/vnd.apache.arrow.stream`
when negotiated; the Arrow path stays deferred under **P11** until
demand is measured (`aidocs/16 P11` is queued — deferred).

**Why this is a *separate* surface, not a Surface-A endpoint.** The
*request* shape (a constrained SQL string) is REST-shaped and
expressible in OpenAPI (`requestBody: type: object, properties: { sql:
type: string, params: type: object }`). The *response* shape (a CSV
or Arrow stream) is not a JSON envelope. The OpenAPI spec describes
it as `responses: 200: content: { text/csv: {schema: type: string,
format: binary}, application/vnd.apache.arrow.stream: ... }` — the
generator emits a method that returns `bytes` / `Buffer`. Same
`shepard-client.SqlApi`. The user dispatch is via the wrapper:

```python
sh.timeseries.to_pandas(timeseries_id, start=..., end=...)  # → calls /sql/timeseries internally post-cutover
```

**This is the keystone insight of the integration.** We are *not*
introducing PostgREST, Hasura, or a sidecar SQL gateway. We are
adding **one endpoint** with a richer response body, behind the
same auth, in the same OpenAPI document, generated by the same
pipeline, called by the same convenience wrapper. The *paradigm*
("SQL over HTTP") becomes a content-negotiation choice, not a
protocol fork.

**Schema discipline.** A curated allow-list of views over
`shepard_public.timeseries`, `shepard_public.annotated_timeseries`,
`shepard_public.ts_minute_bucketed` — selected at handler level,
not parsed as raw SQL. Predicates are parameter-bound (the C5
fix in `aidocs/16 C5` is a hard prerequisite — see §7 risks).
`statement_timeout` and a row cap close the tail risk.

**Slices absorbed:** §4.1 (bulk timeseries reads). §4.8 (gRPC) is
*not* absorbed and is explicitly omitted — Arrow over HTTP solves
the wire-efficiency case once P11 lands.

### 3.3 Surface C — blob side door

S3-presigned URLs for `FileContainer.payload` and
`StructuredDataContainer.payload` per `aidocs/16 P12`, carrying
`aidocs/23 §4.2`. Two new endpoints, one per direction:

- `POST /file-containers/{id}/payload/upload-url` → returns a
  presigned PUT URL + expected SHA-256 + lifetime (≤ 5 min, see
  §7 risks).
- `GET /file-containers/{id}/payload/{oid}/download-url` → returns
  a presigned GET URL.

The legacy proxy paths (`FileRest.java:163-244` for upload at line
163, download at line 182, the OID-bearing path at line 204) stay
behind a deprecation banner per `aidocs/23 §4.2`. The OID-as-path
leak called out in `aidocs/23 §2.1` is replaced by a server-issued
opaque presigned URL: clients never see the OID after the cutover
window.

**No second auth model.** The shepard backend issues the presigned
URL using *its* S3 credentials, bound by the presigning TTL. The
caller never gets long-lived S3 credentials. The same `@Authz`
annotation (F1) that gates the metadata GET also gates the
presign; the presign is just one more JAX-RS handler under the
same authz seam. This is what keeps the surface inside the budget.

**Slices absorbed:** §4.2 (blob payloads). Same shape applies to
`StructuredDataContainer.payload` (`StructuredDataRest`) and to
file-typed references; one implementation, three handlers.

### 3.4 Surface D — change-feed side door

Server-Sent Events at `GET /collections/{id}/events` per
`aidocs/16 P13`, carrying `aidocs/23 §4.4`. Emits
`entity-created`, `entity-updated`, `permission-changed`,
`annotation-added` events on `text/event-stream`. Quarkus support
is built in (`@Produces(MediaType.SERVER_SENT_EVENTS)`).

**Same OpenAPI definition.** OpenAPI 3.1 supports SSE-shaped
responses via `responses: 200: content: { text/event-stream: ... }`.
If the active 3.0 tooling chokes on it (the path is generator-
specific), the endpoint is documented in the static spec until P15
lands and unblocks the typing — but it lives inside *the same
spec file*.

**Same auth, same generator pipeline.** The generated Python
client exposes the endpoint as a `Generator[Event, None, None]`-
typed method backed by `httpx-sse` (vendored if necessary; the
generated client already pulls `urllib3`). The TS client uses
native `EventSource`. The convenience wrapper exposes:

```python
for event in sh.collections.events(id, since=cursor):
    handle(event)
```

**Slices absorbed:** §4.4 (subscriptions / change-feeds). Inbound
shape only — the existing outbound `SubscriptionRest` webhooks
(security finding C4 in `aidocs/01-repo-overview.md:75`) stay as
a separate concern, untouched by this surface.

### 3.5 Explicitly out — argued against

Each absent paradigm has a citation back to `aidocs/23 §4` and a
budget reason:

- **GraphQL** (`aidocs/23 §4.7` / §4.3). Adds a second schema
  source of truth (`schema.graphql` + resolver wiring) and breaks
  the path-shaped permissions model. Violates **2.2** (one
  schema). Out.
- **Hypermedia / HAL / Siren** (`aidocs/23 §4.6`). Adds envelope
  bytes per response in exchange for "discoverability" the
  OpenAPI spec already provides. Violates **2.4** (best-of-breed
  for marginal benefit). Out.
- **gRPC** (`aidocs/23 §4.8`). Adds `*.proto` schema source and
  an Envoy / `grpc-web` deployment. Violates **2.2** and **2.3**.
  Wire-efficiency case absorbed by Arrow under P11 if and when
  needed. Out.
- **Wholesale Quarkus reactive (Mutiny)** (`aidocs/23 §4.9` /
  `aidocs/16 A2`, **P20**). Done *only* on the timeseries read
  path as the first slice; not a wholesale rewrite. Inside the
  budget because it's a code-shape change, not a wire-shape
  change.
- **Apache Arrow Flight** (`aidocs/23 §4.1` / `aidocs/16 P11`).
  Deferred until P10 is in the field and Arrow demand is
  measured. Listed in `aidocs/16` but not on the 12-month critical
  path in §8.

The maintainability win of *not adopting these now* is concrete:
no second schema, no second deployment artefact, no second auth
shim, no extra image in `docker-compose.yml`.

---

## 4. One schema, one generator pipeline

The surfaces in §3 are usable only if the client-generation story
holds them together. Building on `aidocs/23 §5` and the wrapper
design in `aidocs/27`:

### 4.1 Source of truth

**Annotation-generated OpenAPI**, per `aidocs/23 §5.5`. Stay; do
not migrate to TypeSpec or Smithy until multi-protocol emission is
a goal, which it is not (§3.5). The `MicroProfile OpenAPI`
annotations on each JAX-RS handler emit
`backend/target/openapi/openapi.json` at build time. The four
surfaces all live in this one file.

Migrate to **OpenAPI 3.1** once `smallrye-open-api 4.x` is stable —
**P15**, blocked on tooling per `aidocs/16`. P15 is *not* on the
critical path; the four-surface picture works on 3.0 too. P15
removes the `patch_openapi_for_python.py:1-37` post-process step
(`aidocs/23 §5.1`), which is technical-debt cleanup, not a
prerequisite.

### 4.2 Generator

**`openapi-generator-cli`, pinned to one version across Java /
Python / TypeScript** per `aidocs/16 P17`. Today the versions
drift (`v7.12.0` for Python/TS, `v7.16.0` for Java —
`aidocs/23 §5.1`); the pin is a single MR touching three CI files
(`.gitlab/ci/clients/{python,java,typescript}.gitlab-ci.yml`).

**Microsoft Kiota** stays a parallel PoC per `aidocs/23 §5.5`.
Time-boxed: one minor version. Kill if it doesn't deliver a
measurable LoC / bundle / ergonomics win against the same spec.
Open question §9 covers which language to PoC first.

Stainless, Smithy, TypeSpec, oapi-codegen, OpenRPC, tRPC — all
evaluated and rejected in `aidocs/23 §5.4`; none re-debated here.

### 4.3 Schema discipline — the keystone

**P17b CI lint:** every IO class on the backend carries
`@Schema(name = …)`. Today 60-odd `*IO.java` classes have it (e.g.
`DataObjectIO.java:13`); a few slip through and the generator
emits `FooIO` instead of `Foo` (`aidocs/23 §2.11`). Lint is a
single `grep -L '@Schema(name'` over `*IO.java` files. Failure
fails the build.

**Why this is the keystone:** the convenience wrapper hard-codes
imports like `from shepard_client.api.collection_api import
CollectionApi` (per `aidocs/27 §7`). A name flip on regeneration
would break the wrapper at user `pip install` time. P17b moves
that failure to CI build time, which is what makes the wrapper
maintenance bounded. Without P17b, the wrapper is a continuous
drift hazard; with it, the wrapper drifts only when the API
intentionally drifts.

### 4.4 Convenience layer — the multiplexer

**Exactly one wrapper per language**, per `aidocs/16 P16` and
`aidocs/27`:

- `shepard-py`: ~150 LoC core (Phase 1) + ~30 LoC helpers (Phase
  2), wraps `shepard_client.ApiClient`, exposes `sh.collections`,
  `sh.timeseries`, `sh.search`, …, ships pagination iterators,
  ships `to_pandas` / `to_excel` / `ro_crate` (`aidocs/27 §3.5,
  §10`).
- `shepard-ts`: same shape, browser-and-Node-isomorphic core,
  Node-only helpers under `shepard/node` subpath
  (`aidocs/27 §8`).

**The wrapper is what hides §3 from the researcher.** The user
calls `sh.timeseries.to_pandas(ts_id, start=..., end=...)` and
does not know that:

- Today (pre-P10), the call paginates Surface A's
  `GET /timeseriesContainers/{cid}/timeseries/{tid}/payload`,
  assembles a DataFrame with `pd.concat` bounded by `chunksize`.
- Post-P10 (Phase 4 of `aidocs/27 §10`), the call dispatches to
  Surface B's `POST /sql/timeseries` and decodes one CSV stream.

Same signature, internal branch on `Client.api_capabilities`
(detected via `/versionz` per `aidocs/27 §3.5`). Likewise:
`sh.files.upload(path)` post-P12 transparently uses the presigned
URL flow (Surface C); `for e in sh.collections.events(id):` reads
SSE (Surface D). **The four surfaces never appear in user code.**
Surface A is what the *generated* client looks like; surfaces B /
C / D are what the *wrapper helpers* look like from above.

This is what converts "four surfaces" from a multiplier on
maintenance into a no-op for end users. The generated-client side
of the budget stays at one artefact per language; the convenience
side grows by zero artefacts because the wrapper was already in
the plan (P16).

### 4.5 One CI pipeline

The existing `clients/python.gitlab-ci.yml` and TypeScript
counterpart already build, lint, and publish the generated client.
Per `aidocs/27 §6`, add one `wrapper-tests` stage:

```yaml
.test_python_wrapper:
  stage: test
  needs: [build_python_client]
  script:
    - cd clients/python/shepard
    - pip install -e .[all] pytest pytest-httpserver
    - pytest -q
```

Three new endpoints (P10, P12, P13) add at most three
`pytest-httpserver` mock fixtures plus one integration-mode test
each. All inside `mvn test` on the backend side
(`@Produces(MediaType.SERVER_SENT_EVENTS)` is testable from REST-
assured; presigning is testable against a MinIO sidecar already
candidate-in `docker-compose.yml`; `POST /sql/timeseries`
integrates against the existing TimescaleDB Testcontainer).
Budget **2.3** (test runtime) holds.

---

## 5. User-journey walkthroughs (before / after)

This is where the four-surface picture earns its keep. Six
journeys, each showing what the user *types*. Code blocks are
short; the citations point to where today's flow lives.

### 5.1 Journey 1 — "Hello shepard" (the 14-line prelude)

**Before** — `aidocs/input/input_raw.md:1-22`, verbatim in
`aidocs/27 §2`:

```python
from shepard_client.api_client import ApiClient
from shepard_client.configuration import Configuration
HOST = "..."; APIKEY = "..."
conf = Configuration(host=HOST, api_key={"apikey": APIKEY})
client = ApiClient(configuration=conf)
from shepard_client.api.collection_api import CollectionApi
from shepard_client.models.collection import Collection
collection_api = CollectionApi(client)
collection_to_create = Collection(name="Demo", description="...", attributes={})
created = collection_api.create_collection(collection=collection_to_create)
```

**After** — `aidocs/27 §2`:

```python
import shepard
sh = shepard.Client(host="https://…/shepard/api", apikey="…")
created = sh.collections.create(name="Demo", description="…", attributes={})
```

Three lines. The wrapper is Surface A only; no §3 dispatch.

### 5.2 Journey 2 — "timeseries to Excel"

**Before.** Today the user paginates
`GET /timeseriesContainers/{cid}/timeseries/{tid}/payload` (per
`TimeseriesRest.java:336-348`'s 5-tuple key — yes, the *read* uses
the 5-tuple), assembles a DataFrame, calls `df.to_excel`. ~30
lines including the prelude, exception handling for partial pages,
field mapping, and `openpyxl` import.

**After.**

```python
sh.timeseries.to_excel(ts_id, "out.xlsx",
                       start=datetime(2026, 1, 1), end=datetime(2026, 5, 1))
```

One line. Wrapper dispatches to **Surface A** today (paginate-and-
concat, bounded by `chunksize` per `aidocs/27 §3.5,§9`),
transparently retargets to **Surface B** (`POST /sql/timeseries`)
post-P10 cutover. Signature unchanged.

### 5.3 Journey 3 — "browse a collection"

**Before.** Nine `*Api` instantiations to walk the graph:
`CollectionApi`, `DataObjectApi`, `BasicReferenceApi`,
`SemanticAnnotationApi`, plus the four typed reference Apis
(`FileReferenceApi`, `TimeseriesReferenceApi`, etc.). Each
instantiation requires its own constructor with the shared
`ApiClient`.

**After.**

```python
col = sh.collections.get(id)
for do in col.dataobjects.iter():        # lazy, paginates Surface A
    print(do.name, list(do.references.iter()))
```

`col.dataobjects` is the pagination iterator from `aidocs/27 §3.3`
fanned out under one `*Api`-shaped proxy. Surface A only; the
wrapper hides nothing protocol-wise here, just collapses the
nine-class import noise.

### 5.4 Journey 4 — "publish a 5 GB file"

**Before.** Streamed `POST` through the API (`FileRest.java:244`
upload). If the connection drops mid-upload, retry from byte 0.
Heap pressure on the Quarkus app, per `aidocs/23 §4.2`.

**After.**

```python
sh.files.upload(container_id, "/data/big.h5")
```

Wrapper, under the hood:

1. `POST /file-containers/{id}/payload/upload-url` → presigned PUT URL
   (Surface C).
2. Client uploads directly to S3 (resumable; client-side multipart
   if available).
3. `POST /file-containers/{id}/payload?uploadedAs=…` registers
   the URI; backend verifies SHA-256.

The two-step is hidden in the wrapper. Surface A stays valid for
small payloads / clients that don't follow redirects (the deprecation
window in `aidocs/23 §4.2`).

### 5.5 Journey 5 — "watch a collection for changes"

**Before.** Client polls `GET /collections/{id}` every 30 s and
diffs.

**After.**

```python
for event in sh.collections.events(id, since=cursor):
    if event.type == "annotation-added":
        ingest(event.payload)
```

**Surface D** (SSE) under the hood. Same auth, same OpenAPI
endpoint, native `EventSource` in TS / `httpx-sse` in Python. The
wrapper resumes from the last `id:` line on disconnect.

### 5.6 Journey 6 — "publish to Databus"

**Before.** Backend job per `aidocs/16 S2` (parked — dataship-side).
User hits an admin endpoint.

**After.** Same. The user-visible surface does not change; the
orchestration is in `shepard-dataship`. The synthesis does *not*
add a paradigm here — it is called out so the reader sees which
journeys were *not* perturbed. Stability is a feature.

---

## 6. The maintenance-cost ledger

One row per surface plus the wrapper. Columns: new endpoint count,
new schema fragment, new test type, new CI step, on-call burden
delta. "Δ" = change vs. today.

| Surface | New endpoints | New schema fragment | New test type | New CI step | On-call burden Δ |
|---|---|---|---|---|---|
| **A — REST core** | 0 net (rationalised: PATCH P21 adds verbs, A2 redistributes) | OpenAPI 3.0 → 3.1 (P15, blocked on tooling) | none new | P17b lint job (XS) | −1 (RFC 7807 + F1 + L6 reduce auth/error/pagination drift) |
| **B — SQL side door (P10)** | +1 (`POST /sql/timeseries`) | Same OpenAPI doc; new content type `text/csv` (already supported by JAX-RS) | TimescaleDB Testcontainer integration test (already in `mvn test`) | none (existing) | +0.5 (one new SLO: query-timeout enforcement; bounded by `statement_timeout`) |
| **C — Blob side door (P12)** | +2 (`upload-url`, `download-url`) per kind, ×2 kinds = **4** | Same OpenAPI doc; presigned URL is just a string in the response | MinIO sidecar in `docker-compose.yml` (already candidate per `input_raw.md:703`) | none (existing) | +0.5 (presigning TTL alarm; S3 credential rotation) |
| **D — SSE side door (P13)** | +1 (`/collections/{id}/events`) | Same OpenAPI doc; `text/event-stream` content type | REST-assured streaming test (Quarkus built-in) | none (existing) | +0.5 (proxy-survival check, see §7 risks) |
| **Wrapper (P16)** | 0 (consumes generated clients only) | n/a (consumes spec) | `pytest-httpserver` mock + integration mode | One `wrapper-tests` stage per language | −2 (hides four surfaces from researchers; ~14 lines → 3) |

**Sum of endpoint Δ:** **+6** (1 + 4 + 1). Compare to "naively
adopt §4 of `aidocs/23`": +1 SQL + ≥4 presigned + 1 SSE + 1 NDJSON
+ ≥1 GraphQL + ≥1 Arrow Flight = ≥9, plus three new generated
clients, plus two new schema files. The synthesis lands ⅔ the
endpoints, **same one** schema source, **same three** generated
clients, plus the convenience wrapper that was already in P16.

**Sum of on-call burden Δ:** −1 (A) + 0.5 (B) + 0.5 (C) + 0.5 (D)
+ −2 (wrapper) = **−1.5**. Net negative because Surface A's
hardening (RFC 7807, F1, L6, P21) reduces drift faster than B / C
/ D add new failure modes, and the wrapper absorbs the cognitive
overhead of having four surfaces.

**Verdict against the budget (§2):** **Fits.** Two artefacts per
language (2.1) holds — generated client + wrapper. One schema
source (2.2) holds — every surface lives in
`backend/target/openapi/openapi.json`. Test runtime (2.3) holds —
no new harness, only one MinIO sidecar that is already a candidate
per `input_raw.md:703`. Survivable-in-five-years (2.4) holds — no
new vendor lock-in, no new fashionable framework.

---

## 7. What can break the synthesis (risks)

Each risk has a tripwire — a concrete check or alarm that catches
the failure mode before it becomes incident.

**7.1 SSE proxying through Caddy / Keycloak.** The existing
reverse-proxy chain (Caddy → Keycloak → Quarkus, per `docker-
compose.yml`) buffers responses by default; SSE requires
streaming. **Tripwire:** before P13 ships, an integration test
hits `GET /collections/{id}/events` *through the full proxy stack*
(not just against Quarkus directly) and asserts the first event
arrives within 1 s. If the test fails, P13 is gated on a Caddy /
Keycloak config change before it can ship.

**7.2 S3-presigned + permission cache (post-A4) interaction.**
Pre-signing carries the permission decision into the URL. If
permissions change after the URL is issued but before it expires,
a stale grant escapes the cache. **Tripwire:** presigned-URL TTL
≤ 5 min, bound by the `shepard.permissions.cache.ttl` window
(default `PT5M` per `aidocs/16 A4`). One config: presign TTL ≤
cache TTL, enforced at startup (`@PostConstruct` validator). If
the operator widens the cache TTL without widening the presign
TTL, startup fails.

**7.3 SQL-over-HTTP injection.** Even with an allow-list of
views, the predicate building must use parameter binding only.
**Tripwire:** **C5 fix lands before P10.** `aidocs/16 C5` is
"escalated — now also gates L2c"; this synthesis adds it as a
**hard** gate on P10. If C5 slips, P10 ships *with* C5 in the
same MR (the SQL-side parameter binding is the natural test
ground for the Cypher-side fix; one PR, two surfaces). A
follow-up CI lint scans `*Repository*.java` for string-formatted
SQL/Cypher; greps for `format(`, `+ "`, `String.format` inside
`@Repository` classes.

**7.4 Convenience wrapper drift.** The wrapper hard-codes
imports against the generated client; a name flip breaks it.
**Tripwire:** **P17b lint** (every IO has `@Schema(name=…)`) plus
the wrapper's import-time coverage check from `aidocs/27 §7`
(walks `dir(shepard_client.api)`, warns on unwrapped Apis). Two
layers: the lint catches schema-name flips at backend CI; the
import-time check catches new Apis at wrapper publish time.
Wrapper version pin (`shepard-client>=X,<Y`) per `aidocs/27 §6`
catches the runtime mismatch.

**7.5 Generator version pinning drift.** P17 pins one generator
version across three languages; bumping that version on one
language without the others reintroduces the drift it was
supposed to fix. **Tripwire:** one MR per bump touching
`.gitlab/ci/clients/{python,java,typescript}.gitlab-ci.yml`
together. If a bump lands in only one file, the wrapper's
runtime `shepard-client>=X,<Y` range emits a `UserWarning`. CI
diff-test on the three CI YAML files: if one changes without the
others, the MR is rejected.

**7.6 OpenAPI 3.1 timing slip (P15).** P15 is blocked on
`smallrye-open-api 4.x`. If the upstream slips, the synthesis
still works on 3.0; only the SSE response-shape typing is
imperfect. **Tripwire:** Surface D's generated method on Python
and TypeScript is verified end-to-end at wrapper integration time
even on 3.0 — the spec describes `text/event-stream` as a
binary-stream content type the wrapper unpacks. No critical-path
dependency on P15.

---

## 8. Sequenced rollout (12 months, two tracks)

Reusing the `aidocs/20-epic-roadmap.md` two-track frame. Each item
is mapped to its existing backlog ID; new IDs are limited to two
(see end of section).

### 8.1 Foundations track — Surface A hardening

End state: Surface A is RFC-7807, parameterised, lint-protected,
PATCH-aware, declaratively authorised.

| Order | Item | Backlog ID | Size | Notes |
|---|---|---|---|---|
| 1 | Declarative `@Authz` seam replaces path-segment switch | **F1** (= P5) | M | Unblocks anything that touches `PermissionsService.isAllowed`. |
| 2 | Cypher / SQL parameter binding | **C5** | M | Hard gate on P7 (`/search/v2`) and P10. |
| 3 | RFC 7807 error envelope | **H4** (= P18) | S | Bundles with API versioning P4 to avoid a second response-shape break. |
| 4 | OpenAPI 3.1 migration | **P15** | M | Blocked on `smallrye-open-api 4.x`; not on critical path. |
| 5 | Pin generator + Kiota PoC + IO-name lint | **P17 + P17b** | S + XS | Keystone for wrapper maintenance (§4.3). |
| 6 | PATCH adoption per `aidocs/26 §3` finding #1 | **P21** | M | Bundles with P4. |

### 8.2 User-value track — Surfaces B / C / D land in user-visible order

End state: Surfaces B / C / D are live; the wrapper absorbs each
as it lands.

| Order | Item | Backlog ID | Size | Notes |
|---|---|---|---|---|
| 1 | Convenience wrapper Phase 1 | **P16-1** | S | Surface A consumer only; ships value before any new surface. |
| 2 | S3-presigned URLs for blobs | **P12** | M | Surface C; absorbed by `sh.files.upload`. |
| 3 | SQL-over-HTTP for timeseries | **P10** | M | Surface B; gated on C5. Wrapper Phase 4 retargets `to_pandas`. |
| 4 | Convenience wrapper Phase 2 (helpers) | **P16-2** | S | `to_pandas` / `to_excel` / `ro_crate`; ships against P10 if available, falls back to Surface A. |
| 5 | SSE change-feed | **P13** | M | Surface D; absorbed by `sh.collections.events`. Gated on §7.1 proxy-compat test. |

### 8.3 Critical path

Six lines, alternating tracks:

1. **F1** (declarative authz) — unblocks the §3.5 path-fragility tail.
2. **P16-1** (wrapper Phase 1) — three-line "hello shepard" lands.
3. **C5** (parameter binding) — gates P10 and `/search/v2`.
4. **P12** (presigned blobs) — first new surface, biggest user win on the upload-resumability axis.
5. **P10** (SQL over HTTP) — answers the original "timeseries to Excel" prompt.
6. **P13** (SSE change-feed) — completes the four-surface picture.

H4, P15, P17, P17b, P21 land in parallel as foundation hardening.
The unified search **P7** lands when E2 in `aidocs/20` reaches it;
not on this critical path because it is its own epic, but it
absorbs Surface A's `aidocs/13` slice.

### 8.4 New backlog IDs

Two new IDs proposed, both narrow:

- **P22 — SSE proxy-compatibility integration test.** A
  `HealthzIT`-style test that exercises `GET /collections/{id}/events`
  through the full Caddy → Keycloak → Quarkus stack, asserting
  first-event latency. Tripwire for §7.1. **Size XS.** Blocks P13.
- **P23 — Presign-vs-cache TTL invariant validator.** A
  `@PostConstruct` startup check that fails fast if
  `shepard.s3.presign.ttl > shepard.permissions.cache.ttl`.
  Tripwire for §7.2. **Size XS.** Blocks P12 cutover (not the
  initial ship).

These are the only two new IDs introduced. Everything else maps to
an existing item in `aidocs/16-dispatcher-backlog.md`.

---

## 9. Open questions for the maintainer

- **9.1 S3-compatible store choice.** MinIO sidecar in
  `docker-compose.yml`, or external (DLR-managed S3-compatible
  endpoint)? `input_raw.md:703` says "in work externally"; P12's
  scope depends on which side owns the operational tail.
- **9.2 SSE proxy compatibility.** Does the existing Caddy +
  Keycloak chain pass `text/event-stream` without buffering
  today? If not, P22 is itself gated on a proxy config change.
- **9.3 `shepard-py` sync vs async-first.** `aidocs/27 §11`
  question 3 — sync is recommended for v0.1, async deferred to
  post-Phase 3. Confirm.
- **9.4 Kiota PoC target language.** Python (largest user surface,
  most boilerplate per `aidocs/23 §2.10`) or TypeScript (richest
  fluent-path-builder benefit per `aidocs/23 §5.4`)? Pick one for
  the time-boxed PoC.
- **9.5 `POST /sql/timeseries` scope.** Curated views only, or
  free-form `SELECT` against a sandboxed user role? `aidocs/23 §8`
  flags this as the central design choice for P10. The synthesis
  recommends curated-views-only for the initial ship; free-form is
  a P10 follow-up.
- **9.6 P15 timing.** When does the Quarkus extension chain
  shepard depends on cut over to `smallrye-open-api 4.x`? P15 is
  blocked on this; the four-surface picture works either way, but
  the `patch_openapi_for_python.py:1-37` removal is gated.

---

## 10. References

- `aidocs/13-search-improvements.md` — unified `POST /search/v2`
  (absorbed into Surface A via P7 / E2).
- `aidocs/16-dispatcher-backlog.md` — F1 / C5 / H4 / P10 / P12 /
  P13 / P15 / P16 / P17 / P17b / P21 / P7 / P19 / P20 / L6 / A2 /
  A4. Two new IDs proposed: **P22**, **P23**.
- `aidocs/18-pagination-inventory.md` — pagination conventions
  (38 endpoints, four conventions; the `?page&size` rollout
  + cursor on `/search/v2`).
- `aidocs/20-epic-roadmap.md` — two-track framing (Foundations vs
  User-value); E1, E2, E5, E11, E12.
- `aidocs/23-api-critique.md` — §4 (per-slice paradigms, adopted
  wholesale), §5 (client generation, adopted wholesale), §6
  (sized recommendations, mapped to backlog), §7 (things to
  deliberately not do, reinforced).
- `aidocs/26-crud-consistency.md` — the inconsistencies the
  synthesis fixes via P21 / F1 / H4 / P9.
- `aidocs/27-convenience-clients-design.md` — wrapper as
  multiplexer; design landed in commit `a2689f7`.
- Code citations:
  `backend/src/main/java/de/dlr/shepard/data/timeseries/endpoints/TimeseriesRest.java:336-348`
  (5-tuple keying, Journey 2),
  `backend/src/main/java/de/dlr/shepard/data/file/endpoints/FileRest.java:163-244`
  (payload paths, Journey 4),
  `backend/src/main/java/de/dlr/shepard/auth/permission/services/PermissionsService.java:198-243`
  (path-segment switch, F1 motivation),
  `backend/src/main/java/de/dlr/shepard/common/error/ApiError.java:1-11`
  (non-7807 envelope, H4 motivation),
  `.gitlab/ci/clients/{python,java,typescript}.gitlab-ci.yml`
  (P17 pinning surface),
  `scripts/shepard_scripts/scripts/patch_openapi_for_python.py:1-37`
  (P15-removed post-process step).
