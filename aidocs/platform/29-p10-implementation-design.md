---
stage: fragment
last-stage-change: 2026-05-23
---

# P10 — `POST /sql/timeseries` Implementation Design

**Snapshot date.** 2026-05-05.
**Scope.** Focused implementation note for backlog item **P10** — a curated
SQL-over-HTTP endpoint that answers the maintainer prompt
"timeseries to excel via sql" (`aidocs/input/input_raw.md:5-8`). P10
is the post-cutover backend for the convenience wrappers
(`aidocs/ops/27-convenience-clients-design.md` Phase 4) and the curated
read counterpart to **P11** (Arrow Flight, deferred) and **P14**
(NDJSON streaming ingest).

This document is companion to `aidocs/platform/23-api-critique.md` §4.1 and §6
(paradigms — bulk timeseries reads), and aligns with
`aidocs/data/12-timescaledb-performance-analysis.md` §11 (streaming read
path; identifier discipline). No code is shipped here.

**Status legend** matches `aidocs/semantics/13-search-improvements.md`:
🟥 HIGH / 🟧 MED / 🟨 LOW / 🟪 ARCH / ✅ DONE.

---

## 1. Goals and non-goals

**Goals.**

- One round-trip bulk timeseries read for analytics clients.
- `text/csv` direct-paste support for Excel and pandas.
- Permission-respecting: never returns rows from a container the
  caller cannot `Read`.
- Safe by construction: no string-concatenated SQL anywhere — the
  same hazard that produced the C5 Cypher finding
  (`PermissionsDAO.java:14`).
- Stable, OpenAPI-representable request body.

**Non-goals.**

- Cross-DB joins (Neo4j ⨯ Postgres ⨯ Mongo). The endpoint exposes
  one TimescaleDB hypertable and at most a small fan of read-only views.
- Mutations of any kind. P10 is `POST` for body shape, not for
  state change.
- User-defined views or raw arbitrary SQL.
- Arrow Flight on the wire — that is **P11** and explicitly deferred.
- GraphQL-style nesting or response shaping beyond `select`/`group_by`.

---

## 2. Request-body shape — JSON DSL

Three candidates were considered:

- **A. JSON DSL** with `select`, `from`, `where`, `group_by`,
  `order_by`, `limit`, mandatory `time_between` filter. Server
  compiles to a parameterised TimescaleDB `PreparedStatement`.
- **B. Raw SQL string** validated against an allow-list and an AST
  scrubber.
- **C. Templated query language** (PromQL subset / our own tiny DSL).

**Recommendation: A (JSON DSL).** Defence:

- **C5 hazard.** Raw SQL on the wire (B) reproduces the same
  fragility as the Cypher injection issue documented in C5: every
  defensive layer (allow-list, AST scrubber) is one bug away from
  exploitable. A JSON DSL never accepts SQL fragments from the client;
  the compiler emits placeholders only.
- **Tooling.** A JSON Schema is what
  `aidocs/ops/27-convenience-clients-design.md` Phase 4 needs to retarget
  `to_pandas` / `to_excel` / `to_arrow` non-breakingly — the wrapper
  builds a typed dict, not an SQL string.
- **Audit log readability.** Future audit work (F3 territory) gets a
  structured payload it can index, not a SQL blob to regex.
- **OpenAPI representability.** Generated clients (`shepard-py.sh`,
  `shepard-ts.sh`) get typed request models for free.

### 2.1 Grammar

```json
{
  "select": [
    {"col": "time"},
    {"col": "value_double", "as": "value"},
    {"agg": "avg", "col": "value_double", "as": "avg_value"},
    {"col": "timeseries_id"}
  ],
  "from": "timeseries_data_points",
  "where": {
    "time_between": {"start": "2026-01-01T00:00:00Z", "end": "2026-02-01T00:00:00Z"},
    "container_id_in": [42, 43, 44],
    "filters": [
      {"col": "timeseries_id", "op": "in", "value": [101, 102]},
      {"col": "value_double", "op": "gte", "value": 0.0}
    ]
  },
  "group_by": [
    {"col": "timeseries_id"},
    {"time_bucket": "PT1H", "col": "time", "as": "bucket"}
  ],
  "order_by": [{"col": "bucket", "dir": "asc"}],
  "limit": 1000000
}
```

**Required.** `select`, `from`, `where.time_between`. The compiler
rejects any spec missing those — see §6.

**Operators.** `eq`, `ne`, `lt`, `lte`, `gt`, `gte`, `in`, `between`.
No `like`, no `regex` in v1 — they have no use case for numeric
timeseries and they widen the surface. `select` accepts `{col}`,
`{col, as}`, or `{agg, col, as}` where `agg ∈ {avg, min, max, sum,
count, first, last}`.

**Aggregation.** `time_bucket: "PT1H"` parses as ISO-8601 duration
and compiles to TimescaleDB `time_bucket('1 hour', time)`. The
duration is the only field a client can put adjacent to a column
name in SQL — and it is parsed before compilation, never inlined.

---

## 3. Exposed schema

The DSL addresses **one hypertable and one optional view**:

- **`timeseries_data_points`** — the existing TimescaleDB hypertable
  defined in
  `backend/src/main/resources/db/migration/V1.0.0__setup_timeseries_tables.sql:13-31`.
  Columns: `timeseries_id`, `time`, `value_double`, `value_long`,
  `value_string`, `value_boolean`. The composite index
  `(timeseries_id, time DESC)` restored in V1.8.0 is what every
  realistic P10 query plans against.
- **Optional view** `v_timeseries_authorised` — illustrative only;
  ship only if §4 measurements show the IN-list filter is hot. Don't
  bake authorisation into a view in v1; that is F7 territory and
  rejected for now.

```sql
-- ILLUSTRATIVE — do not ship as-is. Lands at V1.9.0 only if needed.
CREATE OR REPLACE VIEW v_timeseries_authorised AS
SELECT dp.timeseries_id,
       dp.time,
       dp.value_double, dp.value_long, dp.value_string, dp.value_boolean,
       ts.container_id
FROM timeseries_data_points dp
JOIN timeseries ts ON ts.id = dp.timeseries_id;
```

**Not exposed.** Anything in MongoDB, Neo4j, or Postgres metadata
tables; `flyway_schema_history`; the migration_progress tables; the
PostGIS spatial-data tables. Those need different permission
semantics and have no compelling bulk-CSV use case.

**Allow-list location.** Java enum `AllowedTable` and `AllowedColumn`,
not a string list in `application.properties`. The wrong choice is
config — the right choice is code, because adding a column should
require a code review and a migration, not an env var bump.

---

## 4. Permission flow — Cypher-first

Two approaches were considered:

- **Cypher-first.** Resolve the user's allowed `containerId` set in
  Neo4j via the (post-P2) `PermissionsService.filterAllowedForUser`
  primitive, then constrain the SQL to
  `WHERE container_id IN (?, ?, …)`. Single Cypher round-trip plus a
  single SQL stream.
- **Row-level security in TimescaleDB.** Denormalise permissions
  into Postgres and let RLS gate every row. This is F7 territory and
  rejected for v1 — it requires a permission-replication pipeline
  Shepard does not have.

**Pick: Cypher-first.** The post-P2 primitive does exactly what we
need; the post-A4 permission cache caps repeated calls at a 5-minute
TTL.

```java
// Sketch — illustrative. Belongs in SqlTimeseriesRest / SqlQueryExecutor.
@Authz(action = AccessType.Read, resource = ResourceKind.TIMESERIES_BULK)
public Response query(SqlQuerySpec spec) {
  String user = ctx.getUserPrincipal().getName();
  Set<Long> allowed = permissionsService.filterAllowedForUser(
      spec.requestedContainerIds(),  // may be empty -> all visible
      AccessType.Read, user);
  if (allowed.size() > MAX_CONTAINERS_PER_REQUEST) {        // 1000
    throw new BadRequestException("too many containers; tighten filter");
  }
  if (allowed.isEmpty()) {
    return emptyOk(spec.acceptType());                       // 200, []
  }
  PreparedStatement stmt = compiler.compile(spec, allowed);
  return Response.ok(executor.stream(stmt, spec.acceptType())).build();
}
```

**IN-list cap.** 1000 container ids per request. Above that, return
`400` with a clear message asking for a tighter filter. Beyond ~1000
the planner cost of the IN list overtakes a denormalised-permissions
join — at which point the right answer is F7, not a bigger IN list.

**Empty allowed set.** Returns `200 OK` with an empty body, not
`403`. The caller is allowed to query; they just have no readable
containers in scope. This matches `aidocs/semantics/13-search-improvements.md`
conventions.

**`@Authz` tie-in.** F1 / P5 declarative authorisation is *nice to
have* before P10 but not blocking. Without it, the endpoint wires
the post-P2 primitive inline as sketched above. With it, the
annotation dispatches `filterAllowedForUser` automatically for the
`TIMESERIES_BULK` resource shape.

---

## 5. Content negotiation

| `Accept` | Body | Streaming | Notes |
|---|---|---|---|
| `text/csv` | RFC 4180 | yes | Primary use case. Direct paste into Excel. ISO-8601 timestamps. UTF-8 by default. |
| `application/json` | `{"rows": [...], "truncated": false}` | yes (NDJSON-internal) | Default for browsers; `Link: <next-page>` only if pagination is wired (probably never — P10 is single-stream). |
| `application/x-ndjson` | one row per line, JSON object | yes | For results that don't fit in client memory. |
| `application/vnd.apache.arrow.stream` | — | — | **Deferred to P11.** Listed here so the Java response writer keeps Arrow optional, not as a future retrofit. |

**Default for `Accept: */*`.** `text/csv`. The maintainer prompt
("timeseries to excel via sql") says CSV first; JSON is the polite
fallback for anyone who explicitly asks.

**CSV detail.** RFC 4180 quoting (CRLF line ending, double-quote
escaping). Timestamps as ISO-8601 with offset (`2026-01-01T00:00:00Z`).
UTF-8, no BOM by default. The "BOM only when Excel's `Accept-Charset`
indicates Windows" trick is a nice-to-have — flagged, not required
in Phase 1 or Phase 2.

**Locale-aware separators.** Excel's de-facto comma-vs-semicolon is
locale-driven. Strict RFC 4180 wins by default. The OpenAPI
description tells locale-affected users to set Excel's import
delimiter explicitly. Open question — see §13.

---

## 6. Streaming and limits

What keeps a runaway query from killing the server.

- **Hard maximum row count.** `shepard.timeseries.sql.max-rows`,
  default `1_000_000`. On reaching the cap, the response closes
  cleanly with HTTP trailer `x-shepard-truncated: true`. Mirrors the
  trailer pattern P14 will use for ingest acks.
- **Hard maximum query time.** `shepard.timeseries.sql.max-duration`,
  default `PT60S`. Implemented via PostgreSQL `statement_timeout`
  set on the JDBC connection at request scope. On timeout return
  HTTP `504` with a structured error body.
- **Mandatory time range.** `where.time_between` is required by the
  compiler. The compiler rejects a spec without it before the SQL is
  even prepared — there is no way to ask P10 for the entire
  hypertable.
- **Streaming output.** Quarkus `StreamingOutput` (or Mutiny
  `Multi<String>`). Never `List<TimeseriesDataPoint>` in memory. The
  read path mirrors the *write*-side pattern in
  `TimeseriesDataPointRepository.insertManyDataPointsWithCopyCommand`
  (`backend/src/main/java/de/dlr/shepard/data/timeseries/repositories/TimeseriesDataPointRepository.java:81-121`)
  which holds a single `Connection` open and streams via the PG COPY
  API — P10 holds a single `Connection` and streams from a
  server-side cursor.
- **Backpressure.** `Connection.setAutoCommit(false)` plus
  `PreparedStatement.setFetchSize(10_000)` triggers the JDBC driver's
  cursor mode. If the client closes the socket, the writer's `IOException`
  cancels the statement promptly via `Statement.cancel()`.
- **Row cap interplay.** The cap is enforced in the writer loop, not
  via SQL `LIMIT`. SQL `LIMIT` would force the planner to consider
  shortcut plans that hurt stable timing.

---

## 7. File-level implementation sketch

New files under
`backend/src/main/java/de/dlr/shepard/data/timeseries/sql/` —
greenfield package, no migration of existing code:

| File | Role |
|---|---|
| `SqlTimeseriesRest.java` | `@Path("/sql/timeseries") @POST`, body `SqlQuerySpec`, content negotiation via `@Produces({"text/csv","application/json","application/x-ndjson"})`. Wires `@Authz` once landed; otherwise inline `filterAllowedForUser`. |
| `SqlQuerySpec.java` | DTO for the JSON DSL (§2.1). Bean Validation annotations, `@Schema` for OpenAPI. Immutable record. |
| `SqlQueryCompiler.java` | Translates `SqlQuerySpec` → `PreparedStatement`. Column / table names checked against `AllowedTable` / `AllowedColumn` enums. Values bound as parameters. **No `String.format` against the SQL string.** |
| `SqlQueryExecutor.java` | Runs the `PreparedStatement` against the existing TimescaleDB `DataSource`. Server-side cursor (`setFetchSize`). Streams `ResultSet` into the chosen writer. |
| `CsvWriter.java` / `JsonWriter.java` / `NdjsonWriter.java` | Tiny format emitters; one method `write(ResultSet, OutputStream)`. The Arrow writer is deliberately absent — see P11. |
| `AllowedTable.java` / `AllowedColumn.java` | Enums; the only place column / table names are blessed. Adding one is a code review. |
| `db/migration/V1.9.0__sql_timeseries_views.sql` | **Only if** §3 measurements justify a view. List the trade-off; default is "do not ship". |

**Reuse, do not duplicate.**

- `PermissionsService.filterAllowedForUser` (post-P2).
- The TimescaleDB `DataSource` already configured for
  `TimeseriesDataPointRepository`.
- `application.properties` patterns for limit/duration config; same
  prefix as the existing `shepard.timeseries.*` keys.
- The existing `CsvLineProvider` family
  (`backend/src/main/java/de/dlr/shepard/data/timeseries/utilities/CsvLineProvider.java`)
  if and only if it cleanly fits a `ResultSet` source. Otherwise
  write a fresh `CsvWriter` — copying these classes is faster than
  bending them.

---

## 8. Tests required before shipping

Tests are the load-bearing part of P10 — the C5 coupling means a
weak test suite ships an injection regression.

**Compiler unit tests** (`SqlQueryCompilerTest`).

- Empty `time_between` → `IllegalArgumentException`, never reaches SQL.
- Unknown column → rejected before binding.
- `from` naming a non-allow-listed table → rejected.
- Compiles to a `PreparedStatement` whose SQL contains zero
  user-supplied substrings — assert by parsing the prepared SQL
  back and round-tripping with bound parameters.
- Round-trip through Postgres `EXPLAIN` in an integration test
  guarantees the planner accepts the emitted SQL on the actual
  schema.

**Permission tests** (`SqlTimeseriesPermissionTest`).

- Empty allowed set → `200` with empty body, not `403`.
- Non-empty allowed set → SQL contains exactly that many parameter
  placeholders in the `IN (?)` clause.
- Allowed set above the 1000 cap → `400` with a structured message
  pointing at the offending count.

**Output format tests** (`SqlTimeseriesFormatTest`).

- Same query in `text/csv` / `application/json` /
  `application/x-ndjson` → byte-for-byte identical logical rows
  after parsing each format back to a list of records.
- CSV passes an RFC 4180 round-trip (write CSV, parse with
  `commons-csv` strict mode, compare).
- JSON matches the documented shape `{rows: [...], truncated: bool}`.
- NDJSON ends each line with `\n`, no trailing comma, parseable
  line-by-line.

**Streaming tests** (`SqlTimeseriesStreamingTest`).

- Query that hits the row cap closes cleanly with the
  `x-shepard-truncated: true` trailer; cap is exact (asserted via
  parametric data fixture).
- Query that exceeds `max-duration` returns `504` and the
  client-visible body contains the configured cap value.
- Client-side abort: half-read response causes the test harness to
  observe the SQL statement cancelled within a small budget
  (e.g. <2s).

**Negative SQL-injection regression tests**
(`SqlTimeseriesInjectionTest`). **This is the C5 coupling.**

- `select.col = "value_double; DROP TABLE timeseries_data_points;"`
  → rejected by allow-list before compilation.
- `from = "timeseries_data_points --"` → rejected.
- `where.filters[0].value = "0 OR 1=1"` (string) → bound as a
  literal parameter; SQL stays `value_double >= ?`.
- `time_bucket = "1 hour); DROP …"` → ISO-8601 parse failure, not
  a SQL fragment.
- Unicode and zero-byte payloads in `select.as` → rejected by an
  identifier regex (`[A-Za-z_][A-Za-z0-9_]{0,62}`).
- The full **C5 regression suite belongs here as well** — the
  `PermissionsDAO.findByEntityNeo4jId` `String.format("%d", id)`
  pattern (`PermissionsDAO.java:14`) is being killed in C5; P10
  must never reintroduce that shape, and an explicit test asserting
  "no `String.format` in `SqlQueryCompiler.java`" (via static
  analysis or a unit test that loads the bytecode) makes the rule
  enforceable.

**Integration tests** (`SqlTimeseriesIT`). `@QuarkusIntegrationTest`
against the docker-compose dev TimescaleDB. Reuse the
`HealthzIT`-style harness where possible. Cover one happy-path
query per content type plus the time-range / row-cap interactions.

---

## 9. Prerequisites and gates

🟥 **C5 (Cypher / SQL injection) — landing must precede or accompany P10.**
This is the most important coupling. C5 fixes
`PermissionsDAO.findByEntityNeo4jId` and the family of
string-concatenated query builders. P10 introduces a *new* SQL
surface; if C5 has not landed, the same engineer who wrote
`"WHERE ID(e) = %d".formatted(entityId)` (`PermissionsDAO.java:14`)
will write `"WHERE timeseries_id = %d".formatted(...)` in
`SqlQueryCompiler.java` on a deadline. The negative-test suite in
§8 catches that, but only if the test suite is the gate. Treat
C5 ↔ P10 as a single readiness checkpoint.

🟧 **F1 / P5 declarative `@Authz`** — *nice to have* before P10 but
not blocking. Without it, P10's permission check is wired inline
against the post-P2 `filterAllowedForUser` primitive (see §4).
P10 gains nothing from waiting; F1 gains a real consumer once both
land.

🟨 **L2 ID migration** — P10 is id-shape-agnostic in v1: all ids
remain `Long` end-to-end. When **L2c** flips canonical identifiers
to `appId` strings, the `containerId` parameter type changes and the
generated clients regenerate. Bundle the cutover with the **L2d v2
release** so P10 doesn't ship two breaking changes a month apart.

🟨 **A4 permission cache** — already informally in place; P10
benefits from the 5-minute TTL because the permission flow runs once
per request, then streams. Without the cache, a cold call still
works but the Cypher round-trip dominates short-query latency.

🟪 **F7 row-level security in Postgres** — explicitly out of scope.
P10 design is forward-compatible: replacing the IN-list constraint
with a `SET LOCAL app.user_id = ?` plus RLS policy is a
backend-only swap if F7 ever lands. Don't pre-build for it.

---

## 10. Convenience-wrapper retarget (post-P10)

`aidocs/ops/27-convenience-clients-design.md` Phase 4 is the consumer.
The pre-P10 helpers paginate the existing `getTimeseries` endpoint
(`TimeseriesRest.java:336-348`) and concat client-side; post-P10
they POST one DSL spec and stream the response.

```python
# shepard_py.sh.timeseries
def to_pandas(self, container_id, ts_ids, start, end, agg=None):
    if self._client.feature_enabled("sql-timeseries"):
        spec = {
            "select": [{"col": "time"}, {"col": "value_double"}, {"col": "timeseries_id"}],
            "from": "timeseries_data_points",
            "where": {
                "time_between": {"start": start, "end": end},
                "container_id_in": [container_id],
                "filters": [{"col": "timeseries_id", "op": "in", "value": list(ts_ids)}],
            },
        }
        return pandas.read_csv(self._client.post_stream("/sql/timeseries", spec, accept="text/csv"))
    return self._legacy_paginate_and_concat(container_id, ts_ids, start, end, agg)
```

The wrapper signature does not change. The dispatch is gated by
the same feature-flag the server reports through its info endpoint.

`to_arrow()` becomes the *natural* helper post-P10 — it reads the
NDJSON stream into PyArrow without ever requiring P11. P11 only
matters for clients that want Arrow Flight on the *wire*; the Python
wrapper does not.

---

## 11. Sized rollout — P10a / P10b / P10c

| ID | Size | Content |
|---|---|---|
| **P10a** | S | `SqlQuerySpec` + `SqlQueryCompiler` + endpoint returning `application/json` only, behind feature flag `shepard.features.sql-timeseries.enabled=false`. Permission flow wired against post-P2 primitive. Unit tests + permission tests + injection-regression tests all green. Lands before any client touches it. |
| **P10b** | M | `text/csv` + `application/x-ndjson` content negotiation; streaming output; row + duration caps wired; `Trailer: x-shepard-truncated`; `statement_timeout` enforced. Format tests + streaming tests green. `@QuarkusIntegrationTest` against docker-compose. |
| **P10c** | S | Feature flag default-on; convenience-wrapper retarget (`aidocs/27` Phase 4); doc page in `aidocs/13`'s tone with the JSON DSL grammar reference. Deprecation notice on the legacy paginate-and-concat endpoint. |

Each phase is its own backlog item and lands as its own PR. A4
runtime-toggle pattern means `false → true` is a config change, not
a redeploy.

---

## 12. Risks and tripwires

🟥 **SQL injection.** See C5 coupling in §9. The single highest
risk; the negative-test suite is the mitigation, the
`PreparedStatement`-only architecture is the prevention.

🟧 **Permission cache TTL race.** `filterAllowedForUser` returns the
cached set; if a grant is revoked while the SQL is streaming, rows
for the just-revoked container leak until the stream closes. Bound
by `shepard.permissions.cache.ttl` (5 min) and by the
`max-duration` cap (60 s). Net leak window is ≤ TTL. Document
explicitly in the OpenAPI description; do not pretend it doesn't
exist.

🟧 **Excel CSV quirks.** BOM, comma-vs-semicolon by locale, date
auto-formatting. Document in the OpenAPI description; do not
auto-detect locale from `Accept-Language`. Auto-detection is the
most common silent-corruption bug in this space.

🟧 **Timeseries cardinality.** A DSL query without aggregation over
a year of data on 1000 high-frequency series can return billions of
rows. The 1M default cap protects the server. The user-side error
message must explicitly suggest `time_bucket` aggregation, with an
example, not a stack trace.

🟨 **Reverse-proxy chunked-stream survival.** Caddy and Keycloak in
the dev path both handle chunked responses, but smoke-test in the
deploy environment as part of P10b — long-running streams have
historically tripped on idle-timeout settings.

🟨 **`statement_timeout` interaction with TimescaleDB compression
decompression.** Decompressing a wide-time-range query can spend
minutes inside the storage layer. Verify in P10b that
`statement_timeout` actually fires during decompression, not only
during planning.

---

## 13. Open questions for the maintainer

1. **JSON DSL grammar.** Is the §2.1 shape acceptable, or does the
   team prefer a documented PromQL subset? Decision affects P10a
   directly.
2. **Default row cap.** Is `1_000_000` too tight (analytics use
   case) or too loose (memory budget on small deploys)? Mid-million
   defaults are equally defensible.
3. **One table or a fan of views?** Should P10 expose only
   `timeseries_data_points`, or land a small `v_timeseries_authorised`
   view (§3) that pre-joins container ids? Trade-off is one extra
   migration vs one extra join in every query.
4. **CSV semantics.** Strict RFC 4180 (default), or Excel-friendly
   locale-aware semicolons by `Accept-Language`? See §5.
5. **Deprecation window for paginate-and-concat.** Phase 3 flips
   the flag default-on. How long does the old path coexist —
   one minor release, two, until the next major? Affects
   `aidocs/27` Phase 4 cutover messaging.

---

## 14. Cross-references

- `aidocs/data/12-timescaledb-performance-analysis.md` §11 — streaming
  read path; this design aligns, does not diverge.
- `aidocs/semantics/13-search-improvements.md` — same JSON-shape conventions
  for predicate filters; P10 does not duplicate the `POST /search/v2`
  predicate language because the use cases (free-form search vs
  bulk numeric read) are different.
- `aidocs/16-dispatcher-backlog.md` — backlog IDs P10 / P11 / P14 /
  C5 / P2 / F1 / A4 / L2.
- `aidocs/platform/23-api-critique.md` §4.1 — paradigms motivation;
  §6 — recommendations including P10 + P11.
- `aidocs/platform/26-crud-consistency.md` — existing `TimeseriesRest`
  shape (40+ endpoints; 5-tuple key at `TimeseriesRest.java:336-348`).
- `aidocs/ops/27-convenience-clients-design.md` Phase 4 — the
  consumer; P10c wires the retarget.
- `aidocs/input/input_raw.md:5-8` — the maintainer prompt that
  motivates the work.
