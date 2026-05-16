# 19 — Critical Architectural Feedback

Snapshot date: 2026-05-05. Ground-truthed against
`claude/implement-input-raw-changes-2WiOF` HEAD `2ad179d`,
post-Round-3 dispatcher state (see `aidocs/16-dispatcher-backlog.md`).

This is a maintainer's-eye review of shepard as a system, not a
re-statement of `aidocs/archive/01-repo-overview.md`. Strengths first, then
fragilities prioritised by blast radius, then the seams that aren't
seams yet, then risks specific to the proposals in
`aidocs/semantics/13-search-improvements.md` and `aidocs/semantics/14-semantic-improvements.md`,
then a 6-month recommendation list mapped to backlog IDs in
`aidocs/16-dispatcher-backlog.md`, then a short list of architectural
temptations to resist.

Cross-references: `12-…` for timeseries / read-path,
`13-…` for unified search, `14-…` for annotation generalisation,
`16-…` for backlog IDs.

---

## 1. Where shepard is strong

These are the parts that do not need rework. They are the foundations
to evolve *from*, not towards.

### 1.1 Bounded startup wait is now a uniform primitive

`MigrationsRunner.awaitConnectivity` (`backend/src/main/java/de/dlr/shepard/common/neo4j/MigrationsRunner.java:65-95`)
became a small, deterministic, injection-friendly retry helper after A1
(commit `a74d278`) and A1e (commit `0f2f512`). It is reused by
`NeoConnector.connect` (`backend/src/main/java/de/dlr/shepard/common/neo4j/NeoConnector.java:78-90`).
Two infinite loops collapsed into one tested function with deadline +
exponential backoff, capped sleep, configurable timeout
(`shepard.migrations.connection-wait-timeout=PT60S`), and surfaced
failures rather than swallowed ones. This is the rare case where an
"architectural" fix shrunk the surface area.

### 1.2 Per-DB health state with a single source of truth

`DbHealthState` (`backend/src/main/java/de/dlr/shepard/common/healthz/DbHealthState.java`),
`DbPinger`, and the abstract base classes for Startup / Readiness /
Liveness checks are a clean refactoring of the health surface (A1b,
commit `8f40156`). Each of Neo4j / Mongo / Timescale / PostGIS has a
`Pinger`, a `StartupCheck`, and a `ReadinessCheck` that share state via
a single `DbHealthState`. The `DbRecoveryScheduler`
(`common/healthz/DbRecoveryScheduler.java`) plugs in by iterating
`Instance<DbPinger>`, and PostGIS short-circuits to UP when its toggle
is off (`PostGisPinger.isRequired()` reads
`SpatialDataFeatureToggle.isActive()`). This is the right shape for the
graceful-degradation work tracked as A1c — the seams are already cut.

### 1.3 The polyglot store separation is principled

ADR-008 / ADR-010 / ADR-014 (`architecture/src/09_architecture_decisions/`)
defend the four-store model against a Postgres-only or MinIO+Postgres
alternative with a clear cost basis (32 TB Postgres limit; pgvector
benchmark losing to PostGIS by orders of magnitude; InfluxDB
operability). The seams between stores are visible in code:
`NeoConnector` (Neo4j-OGM), `TimeseriesDataPointRepository` (raw
JDBC/Hibernate against TimescaleDB), `FileService` (Mongo GridFS),
`SpatialDataPointRepository` (Hibernate Spatial). No store leaks into
another's repository layer. This is unusual for a project of this age.

### 1.4 Migration progress is now a first-class entity

P3 (commit `7cc74b8`) added `MigrationProgress` with persisted state, an
HTTP endpoint at `/temp/migrations/state` and `/temp/migrations/{id}`,
and a `MigrationRunner` that resumes from `resumeBatchIndex` after a
crash (`backend/src/main/java/de/dlr/shepard/data/timeseries/migration/services/MigrationRunner.java:25-50`).
The COPY-based bulk-insert path
(`insertManyDataPointsWithCopyCommandBatched`) takes batch reporter +
error reporter callbacks. Resumability built in from day one is rare;
this is the right pattern to copy if a future Neo4j refactor (cluster
E) ever needs a long-running migration.

### 1.5 Caffeine permission cache is sized and tested

A4 (commit `53996a3`) discovered that the cache was already
`quarkus-cache` Caffeine (the input-raw critique was stale) and added
per-cache TTL + max-size config and a behavioural-contract test pinning
hit/miss/TTL/invalidation/LRU. The keying is `(entityId, accessType,
username)` via `CompositeCacheKey` and invalidation only purges the
owning entity (`PermissionsService.removeEntityFromCache`,
`backend/src/main/java/de/dlr/shepard/auth/permission/services/PermissionsService.java:142-148`).
This is the cheapest, most-correct cache layer in the codebase.

### 1.6 Test infrastructure is pluggable

The `MigrationsRunnerTest` injects `Sleeper` and `NanoClock`
(`MigrationsRunner.java:107-119`), and the new health checks have 21
unit tests using a synthetic `DbHealthState`. This is the seam that
makes the architectural-change pace possible at all — tests are not
container-bound, so deterministic behaviour is testable. Generalise it
when adding new infra.

---

## 2. Where shepard is fragile

Ordered by blast radius, not severity. "Blast radius" here is the
number of features and request paths that can be silently broken by
one mistake in this code.

### 2.1 `getRoles` fallback grants full access — C3 still open

**File:** `backend/src/main/java/de/dlr/shepard/auth/permission/services/PermissionsService.java:259-261`.

```java
if (perms.isEmpty()) {
  // Legacy entity without permissions
  return new Roles(false, true, true, true);
}
```

If a `BasicEntity` has no `has_permissions` edge in Neo4j, the
permission check returns *manager + writer + reader* for the calling
user. This is the C3 "permissions fallback inversion" finding from
`07-security-issues.md`. Round 1–3 did not touch it; A4's cache caches
the *result* of a broken predicate.

**Why it matters.** Every new entity creation path is an audit hazard.
Anything that creates a `BasicEntity` in Neo4j without also creating
its `Permissions` node and `has_permissions` edge becomes a public
backdoor. There is no startup audit for the invariant.

**Smallest plausible mitigation.** Flip the fallback to
`Roles(false, false, false, false)` (C3 patch in
`11-implementation-plan.md` Phase 1A); add a startup check that fails
fast if any `BasicEntity` lacks a `has_permissions` edge; emit a
metric. P2's batch-permission helper goes here next.

### 2.2 `Neo4jQueryBuilder` builds Cypher by `+` — C5 still open

**File:** `backend/src/main/java/de/dlr/shepard/common/search/query/Neo4jQueryBuilder.java:198-244`,
and the parallel sites at lines 280, 376, 386.

```java
ret = ret + "(sem.propertyIRI " + operatorString(node.get(Constants.OP_OPERATOR))
    + " \"" + propertyIRI + "\" AND ";
```

Every search endpoint goes through this. The C5 finding is a Cypher
injection at the user-controlled `propertyIRI` / `valueIRI` /
`propertyName` paths. The injection is real; the fix is parameter
binding, not a refactor — and it must not be conflated with the cluster
E Neo4j refactor (`02-cluster-map.md` risk callout 4).

**Blast radius.** Five existing search endpoints (`SearchRest.java:44-204`).
Anything that lands in `13-search-improvements.md` §2 (the unified API)
is built on top of this builder; the builder is the longest-lived line
of defence, and the most exposed.

**Smallest plausible mitigation.** Replace string concatenation with
`.withParameter("…", value)` calls; whitelist property names against
the per-kind enum already enforced upstream by the JSON schema; add an
adversarial fixture suite. Ship as a parameterisation patch — no shape
change.

### 2.3 `ShepardExceptionMapper` echoes raw exception messages

**File:** `backend/src/main/java/de/dlr/shepard/common/exceptions/ShepardExceptionMapper.java:14-26`.

The mapper returns
`new ApiError(status, exception.getClass().getSimpleName(), exception.getMessage())`
for every `Exception` it catches. JDBC errors leak SQL fragments and
column names; Neo4j-OGM errors leak Cypher; Hibernate errors leak entity
class names and field paths. This is H4 from
`07-security-issues.md` and it cross-cuts every endpoint in the
backend.

**Smallest plausible mitigation.** Generic 500 message ("Internal
server error", correlation id);
`WebApplicationException` subclasses keep their messages
(`Status.BAD_REQUEST`, `NOT_FOUND`, etc.); log full detail server-side
only. Bundle with a correlation-id MDC filter — adds observability for
free.

### 2.4 No admin role mechanism — A0 unblocker

**File:** `backend/src/main/java/de/dlr/shepard/auth/security/JWTPrincipal.java:24`,
`backend/src/main/java/de/dlr/shepard/common/filters/JWTFilter.java:218`.

```java
var principal = new JWTPrincipal(audience, issuedFor, username, keyId, new String[0]);
```

Roles in `JWTPrincipal` are always `new String[0]` even though OIDC
realm-access roles are validated at line 207 against the configured
`oidc.role`. `@RolesAllowed("admin")` denies everyone; A3b
(`/admin/features`) was paused because of this discovery. The
existing `PermissionsService.isAllowed`
(`PermissionsService.java:198-235`) special-cases path prefixes
(`temp/migrations`, `users`, `search/users`, `search/containers`) to
bypass entity-id-based checks — i.e. there is no admin model, only
unauthenticated carve-outs.

**Why it matters.** Every "admin" endpoint added now (migration
control, feature toggles, telemetry, future SQL escape hatch in
`13-…` §5, future SHACL rules in `14-…` §6) needs a real authn-authz
story. Without one, every such endpoint either lives behind a
path-prefix carve-out (and is therefore unauthenticated) or denies
everyone.

**Smallest plausible mitigation.** A0 (option a in `16-…`'s open
decisions): populate `JWTPrincipal.roles` from realm-access in the
JWT path; mirror in the API-key path; configurable
`shepard.admin.role`. ~50 lines + config. *Decide before A3b, P3c,
or any new admin endpoint is dispatched.*

### 2.5 `PermissionsService.isAllowed` is a brittle path-segment switch

**File:** `backend/src/main/java/de/dlr/shepard/auth/permission/services/PermissionsService.java:191-235`.

The function has eleven if-branches keyed on path-segment text
(`"temp"`, `"migrations"`, `"users"`, `"search"`, `"containers"`,
`Constants.LAB_JOURNAL_ENTRIES`). Adding a new top-level path
(`/admin/*`, `/v2/search`, `/terms/search` from `14-…` §3.3) silently
falls into "deny" because it doesn't match any branch. Adding a new
sub-resource (e.g. `/timeseries-containers/{cid}/permissions/grants`
proposed by A2's decomposition) lands at
`StringUtils.isNumeric(idSegment) → isAccessTypeAllowedForUser`, which
expects the second segment to be the entity id and breaks for any
other shape.

**Why it matters.** Every API-shape change is bottlenecked on this
function, which has no test coverage adequate for its current
complexity (see Phase 1A acceptance criteria for C3). The unified
`/search/v2` proposal in `13-…` §2.1 will require yet another
branch.

**Smallest plausible mitigation.** Replace the path-segment switch
with a declarative authz rule — JAX-RS interceptor that reads an
`@Authz(...)` annotation per endpoint method; default-deny;
`@Authz(public)` for the public ones; `@Authz(entityIdPath = "{id}")`
for the entity-scoped ones. This is the seam that A3b and any future
admin work need anyway.

### 2.6 Hot read paths still load full result sets into heap

**Files:** `backend/src/main/java/de/dlr/shepard/data/timeseries/repositories/TimeseriesDataPointRepository.java:138-158`
(query path); `backend/src/main/java/de/dlr/shepard/data/timeseries/services/TimeseriesService.java`
(parallel-stream fan-out).

Documented in `12-timescaledb-performance-analysis.md` §5.5 and §11.A.
A 90-day 1 Hz CSV export is ~7.8 M rows ≈ 200 MB of
`TimeseriesDataPoint` objects on heap before the first byte goes out.
The CSV path *appears* streaming — `CsvInputStream` is an
`InputStream` — but its constructor takes an already-materialised
`List`. `application.properties` does not set
`hibernate.jdbc.fetch_size`, so JDBC buffers the whole result client-
side regardless. Concurrent users on big windows produce GC-pause
"sluggishness" that looks like a DB problem and isn't.

**Blast radius.** Every dashboard query, every export, every multi-
series fan-out. The fix is mechanical — `getResultStream()` +
`StreamingOutput` + a fetch-size hint — and is fully described in
`12-…` §11.A.2. Out-of-scope for the dispatcher rounds so far; this
is what S1 in the backlog is for.

### 2.7 `Neo4jQueryBuilder` is the *only* search abstraction

**File:** `backend/src/main/java/de/dlr/shepard/common/search/query/Neo4jQueryBuilder.java`
(796 lines).

Beyond C5, the builder is a hand-rolled JSON-to-Cypher translator
with hardcoded keys (`createdBy`, `createdAt`, `hasAnnotation`,
`hasAnnotationIRI`, `successorIds`, `childrenIds`, `referencedCollectionId`,
`referencedDataObjectId`, `fileContainerId`,
`structuredDataContainerId`, `timeseriesContainerId`, `neighborhoodIds`)
mapped to Cypher fragments. `MongoDBQueryBuilder.java` is a parallel
translator with the same operator set minus a few. New searchable
properties require code changes in *both*. The label-vs-IRI
asymmetry in annotation search (`14-…` §3.1) lives here too.

**Why it matters.** This is the single most concentrated source of
bug velocity (`#683`, `#688`, `#722`, `#758` in `02-cluster-map.md`'s
cluster H). Every new annotation kind (`14-…` §2.1) and every new
predicate operator (`13-…` §2.2) goes through this file.

**Smallest plausible mitigation.** Don't rewrite. Instead, add a
typed predicate model (sealed Java records per predicate kind) above
the builder, then have *one* method per record translate to Cypher
parameters. The builder shrinks from a switch on JSON to a `match`
expression on a sealed type. C5's parameter-binding fix is naturally
the wedge for this.

### 2.8 No structured audit trail; no correlation id

There is no MDC, no request id, no audit log. `ShepardExceptionMapper`
logs to default Quarkus log; `JWTFilter` logs successes and failures to
default log; permission denials are silent (`PermissionsService.isAllowed`
returns `false` and JAX-RS produces a generic 403). H5 in
`07-security-issues.md` ("PublicEndpointRegistry exact-match") is
adjacent but doesn't fix this.

**Why it matters.** The frontend has no way to correlate a 5xx response
with a backend log line. Multi-DB requests (Neo4j → Postgres → Mongo)
have no thread of breadcrumbs across the three logs. Compliance
posture (Cluster F) needs a per-request audit trail anyway.

**Smallest plausible mitigation.** A MDC filter setting `requestId` and
`username` on every request; surface `X-Request-Id` in responses;
emit a single audit-event JSON line per write request (entity, action,
allow/deny, requestId). One container-request filter; ~80 lines.

---

## 3. Cross-cutting concerns

The seams below are where shepard's evolution will be cheap or
expensive depending on a small number of decisions.

### 3.1 Permissions / admin

The model is "user × entity × access-type", cached. Strengths: clear
data-flow; cache is sized; invalidation is local. Gaps:

- **No admin role**. §2.4. Blocks A3b, P3c, and any future admin
  endpoint. *This is the highest-leverage decision in the next 6
  months.*
- **No batch check.** Filter-N-by-permission costs N Cypher queries
  via `parallelStream` (P2 queued). Solution is one Cypher query;
  single new method on `PermissionsService`.
- **Path-based isAllowed switch.** §2.5. Brittle; expensive to extend.
- **Permission inheritance from parent containers** is implemented
  in service layers, not declared. If `14-…` §2.4 lands (annotations
  inherit container permission), there is no single place that says
  so — it has to be re-implemented per bridge node.

**Recommendation.** A0 first; declarative `@Authz(...)` second; P2
batch third.

### 3.2 Observability

Quarkus Micrometer is configured (Prometheus on
`/shepard/doc/metrics/prometheus`) but only exposes default Quarkus
metrics + the existing Hibernate ORM metrics
(`quarkus.hibernate-orm.metrics.enabled=true`, which is also a perf
hazard per `12-…` §7.1). Missing:

- Per-cache metrics on `permissions-service-cache` (A4d queued —
  one config flag).
- Per-DB pinger latency / freshness (the data is in
  `DbHealthState.lastLatencyMs` already; not exported).
- Request-scoped MDC + correlation id (§2.8).
- Per-endpoint timer for the heavy timeseries paths
  (`shepard.timeseries-data-point.batch-insert` / `.query` already
  exist per `12-…` §9; not extended to read paths).
- No SLO surfaced; no panic-button for "Neo4j is recovering, deny
  writes".

**Recommendation.** Land A4d; export `DbHealthState` to Micrometer;
add a request-id MDC filter. Defer SLOs.

### 3.3 Error model

`ApiError(status, type, message)` is the single response shape. It
does not carry a correlation id, a category (validation / permission /
infra), or per-field errors for validation. The
`ShepardExceptionMapper` (§2.3) makes it leaky. There is no
`Problem+JSON` (RFC 7807) shape; OpenAPI generation has to special-
case error responses.

**Recommendation.** When fixing H4, switch the body to RFC 7807; type
URIs documented in OpenAPI; validation errors carry a `errors[]`. Add
a correlation id field. This is breaking for clients that introspect
the body, so bundle with the API-version decision (P4).

### 3.4 Multi-DB consistency

Today: ingest writes Neo4j metadata and Postgres rows in separate
transactions. There is no two-phase commit and no compensating-action
handler. `TimeseriesService.saveDataPoints`
(`backend/src/main/java/de/dlr/shepard/data/timeseries/services/TimeseriesService.java`)
calls `getOrCreateTimeseries` (Postgres upsert) then
`insertManyDataPoints` (Postgres bulk). The Neo4j-side `Timeseries`
node is an OGM projection of the Postgres row, not a separate write.
A failure mid-ingest leaves Postgres ahead of nothing — there's no
divergence vector here.

The future hazard is `14-…` §5.4 ("dual-write to triplestore") and
`13-…` §6 ("cross-store search planner"). Both implicitly assume
that Neo4j → triplestore and Neo4j → Postgres can be kept in sync.
The triplestore sync should be **outbox-from-Neo4j** (`14-…` §5.4
option 2), not synchronous dual-write.

### 3.5 API stability

OpenAPI is generated; clients are auto-regenerated for Java / Python /
TypeScript. There is no `/v1` prefix; every breaking change is a
breaking change for every client.

P4 (queued) proposes `/shepard/api/v1`; it is breaking on its own.
The §11.B identifier-discipline work in `12-…` and the `/search/v2` in
`13-…` cannot land without it (or at least without a deprecation +
parallel-route plan).

**Recommendation.** Pick API-versioning strategy *now* (parallel
prefix vs. content negotiation vs. major-version-only-on-breaking).
Document. All future breaking changes go through it.

---

## 4. Architectural risks for the proposals in `13-…` and `14-…`

The forward-looking design notes are good. They will surprise the team
in the following places if implemented as written.

### 4.1 `13-…` §2.2: `raw.cypher` / `raw.sparql` / `raw.sql` need an authz model that does not exist

`13-…` §8 already calls this out as an open question. With the C3
fallback (§2.1) and no admin role (§2.4), every escape hatch is
either wide-open or denied to everyone. *Sequencing constraint*: A0,
C3, P2 must land before any `raw.*` is shippable.

The `raw.sql` proposal additionally requires Postgres row-level
security tied to the authenticated user — but the authentication
context is JWT in the JVM, not a Postgres role. Either stand up per-
user Postgres roles (heavyweight, contested), or push allow-lists
into the SQL views and `WHERE shepard_user = current_setting('app.user')`
+ a `SET app.user = …` on the connection. The second is what to
build; document the constraint.

### 4.2 `13-…` §2.4: Neo4j fulltext indexes are non-trivial in size

Neo4j fulltext indexes are Lucene-backed and segregated per index. On
a Neo4j instance that already holds the entire metadata graph + every
annotation, adding `(name, description)` fulltext on Collection +
Container + DataObject + Reference + SemanticAnnotation can multiply
the index footprint. Run a sizing exercise on a production-shaped
dataset before going to step 2 of `13-…` §7.

The "third option" of an external index (Elastic / OpenSearch) is
ruled out by the "on-premises only" constraint
(`01-repo-overview.md` section "Project goals & scope"). This means
Neo4j fulltext + Postgres `tsvector` is the *whole* search-indexing
budget; do not let it get crowded out.

### 4.3 `13-…` §6 cross-store planner depends on identifier discipline that isn't done

The planner needs `(containerId, timeseriesId)` returned by Neo4j to be
directly callable on Postgres data endpoints. `12-…` §11.B is the
prerequisite. The dispatcher backlog has §11.B as `S1` (queued, scoped
streaming + id alignment) but it is large and depends on a
deprecation cycle for the 5-tuple parameters. The planner is only
useful once the 5-tuple is no longer the routing key — call this out
in the §13 §7 migration table.

### 4.4 `14-…` §5: n10s on Neo4j is the right first step but has a hidden fragility

Loading a non-trivial ontology (PROV-O alone is ~1 k triples, plus
imports) into the Neo4j that also holds production data shares a
write-ahead-log with the operational graph. n10s materialises triples
as nodes/edges; SPARQL queries traverse them via the same Neo4j
engine that serves `PermissionsService`'s queries. If reasoning
queries become slow, they slow down auth.

**Mitigation.** Even when starting with n10s, run the ontology zone
in a separate Neo4j *database* (Neo4j 4.x+ supports multi-database in
one cluster). Annotation-zone projection writes from the operational
graph; ontology zone is read-mostly. When (not if) the time comes to
graduate to RDF4J / GraphDB (`14-…` §5.2), the seam is already there.

### 4.5 `14-…` §3.2: label-as-cache requires a refresh story that doesn't exist

Today `propertyName` / `valueName` are snapshotted at create time and
never refreshed. Promoting them to "derived data, refreshable" means
a job that re-fetches from the upstream SPARQL endpoint. With one
external endpoint per `SemanticRepository` and possibly many
annotations referencing it, the refresh is an N×M problem with
on-prem latency budget (`01-…` "no internet"). Either:

- Cache TTL is "until next admin-triggered refresh" (manual);
- Or refresh runs against the local triplestore once `14-…` §5
  lands (same condition as `14-…` §3.3 search-as-you-type).

Document which.

### 4.6 `14-…` §2.4: "Annotations inherit parent permission" is more annotations + bridges

Every `Annotatable*` bridge node is unpermissioned and inherits via
the `BasicContainer`. When the C3 fallback (§2.1) is fixed by
defaulting to deny, "container has no permission node" silently
becomes "no annotation read"; legacy data (containers created before
the C3 fix) breaks. Plan a migration step (one-Cypher: every
`BasicEntity` without `has_permissions` gets a default-private
`Permissions`, owner `system`) — this is also tech-debt #1.

---

## 5. Recommendations — the next 6 months

Prioritised. Each maps to backlog IDs from `aidocs/16-dispatcher-backlog.md`
(or proposes a new ID). Sequencing rationale follows; the aim is to
unblock every later piece by landing a small earlier piece.

1. **Land A0** (admin role mechanism). Decision-blocked; needs the
   user to pick option (a)/(b)/(c). Without this, A3b, P3c, and every
   `raw.*` escape hatch from `13-…` are stuck. **~50 lines + config.**
2. **Land C3** (permissions fallback inversion) with startup audit.
   Phase 1A Critical from `11-implementation-plan.md`. Re-cost as
   small after A0; the audit step is the new piece.
3. **Land C5** as a parameterisation patch (Phase 1A Critical).
   *Do not* conflate with cluster E. Add an adversarial regression
   suite as the test gate for §2.7's later refactor.
4. **Land P2 + A4c + A4d** (batch permission check + cache warming +
   metrics; commits queued in Round 3). After C3, this turns the
   biggest hot-path latency hit into one Cypher per request.
5. **Add a declarative `@Authz` annotation + interceptor** to replace
   `PermissionsService.isAllowed`'s path-segment switch. *New ID
   F1 (proposed).* Sketch: a `ContainerRequestFilter` reads `@Authz`
   on the matched JAX-RS resource method, defaults to deny; existing
   call sites migrate one resource at a time. This is the §2.5
   mitigation; it also makes `13-…` §2.1 a one-line change rather
   than a builder edit.
6. **Land H4** (`ShepardExceptionMapper` sanitisation) bundled with
   a `requestId` MDC filter and an RFC 7807 body. *Combine F2
   (proposed) with H4.* Ship under the same release as the
   API-versioning decision (P4) so the breaking shape change is
   absorbed once.
7. **Land S1** — streaming read path end-to-end (`12-…` §11.A.2
   steps 1–5). The single biggest user-visible perf win after the
   schema work in V1.8.0. Do *not* ship cursor pagination (`13-…`
   §2.6) before this — they share `Query.getResultStream()` plumbing.
8. **Begin `13-…` step 1** (unified `POST /search/v2` with predicate-
   JSON layer only, no fulltext, no escape hatches). This is the
   smallest viable wedge for §2.7's refactor and forces the
   typed-predicate-model split.
9. **Begin `14-…` Phase A** (generalised `AnnotatablePayload<ID>`
   bridges). Independent of the Neo4j refactor (cluster E); unblocks
   the long-standing #43 / #553 / #656 cluster.
10. **Stand up A4d / `DbHealthState` Micrometer export + per-request
    MDC** (proposed F3). Three small changes, one observability
    layer; lands the foundation for §3.2.

Backlog mapping summary:
- Existing IDs: A0, A4c, A4d, C3, C5, H4, P2, P4, S1.
- New proposed: **F1** (declarative `@Authz`), **F2** (correlation-id
  MDC + RFC 7807 error model), **F3** (Micrometer export of
  `DbHealthState`).

---

## 6. Things to deliberately *not* do

These are temptations the proposals invite. Each saves a quarter of
effort if avoided.

1. **Do not adopt GraphQL.** Suggested in `input/input_raw.md:1775-1780`.
   shepard's read shape is bulk timeseries data + small graph
   metadata; GraphQL helps with neither (no field-cardinality benefit
   on a 7 M-row payload; the metadata graph already has cheap
   round-trips). The `13-…` unified `POST /search/v2` with `select` /
   `where` / `include` covers the same use cases without a parallel
   schema or N+1-resolver risk. Adding GraphQL also adds a parallel
   client generator, parallel doc tooling, and a parallel auth seam.
2. **Do not introduce an event bus.** Tempting for the dual-write
   problem in `14-…` §5.4 and the future audit log in §2.8. The
   on-premises constraint and the team size do not justify Kafka /
   Pulsar / RabbitMQ in the stack. Start with an outbox table in
   Postgres and an `@Scheduled` consumer (the same primitive A1f
   already uses). If scale demands more, reconsider; today this is
   one container too many.
3. **Do not rewrite `Neo4jQueryBuilder` as part of the unified-search
   work.** §2.7 looks like it wants a rewrite; it doesn't. Add the
   typed predicate layer above; let the builder shrink as predicates
   migrate. A rewrite trades C5's defence-in-depth for a multi-month
   timeline and there is no test coverage to land it on.
4. **Do not consolidate TimescaleDB and PostGIS** (A4b is correctly
   parked). The reasoning in `input_raw.md:1564-1580` ("both are
   Postgres extensions") elides the operational reality:
   memory tuning, vacuum schedules, and chunk-skipping policies for
   timeseries collide with PostGIS query planner needs. The cost is a
   second port; the benefit is a shared backup script. Not worth it.
5. **Do not adopt Hibernate Reactive on the read path.** `12-…` §11.A.3
   is explicit on this; the hazard is event-loop starvation when
   blocking JDBC sits under reactive HTTP. Stay blocking; rely on
   `StreamingOutput` and a worker thread.

---

## 7. References

- Code: `backend/src/main/java/de/dlr/shepard/`
  (`common/neo4j/`, `common/healthz/`, `common/configuration/feature/`,
  `auth/permission/services/PermissionsService.java`,
  `auth/security/JWTPrincipal.java`, `common/filters/JWTFilter.java`,
  `common/exceptions/ShepardExceptionMapper.java`,
  `common/search/query/Neo4jQueryBuilder.java`,
  `data/timeseries/migration/`).
- Companion docs:
  `aidocs/data/12-timescaledb-performance-analysis.md` (timeseries / read
  path; §11 for identifier discipline),
  `aidocs/semantics/13-search-improvements.md` (unified search),
  `aidocs/semantics/14-semantic-improvements.md` (annotation generalisation,
  triplestore).
- Backlog: `aidocs/16-dispatcher-backlog.md` (A0, A1*, A3*, A4*, P2,
  P3*, P4, S1; F1/F2/F3 proposed in §5 above).
- Issue context: `aidocs/archive/02-cluster-map.md` (clusters E, F, H);
  `aidocs/archive/07-security-issues.md` (C1–C5, H1–H8);
  `aidocs/platform/11-implementation-plan.md` (Phase 1A/1B).
- Architecture: `architecture/src/09_architecture_decisions/` (ADR-008
  polyglot persistence; ADR-019 member injection).
