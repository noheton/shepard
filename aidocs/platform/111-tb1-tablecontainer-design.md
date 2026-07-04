---
stage: feature-defined
last-stage-change: 2026-05-31
audience: contributors, plugin authors, operators
supersedes: project_table_container.md (consolidated), casestudy_table_container.md (consolidated), aidocs/40 §140 (catalog row)
related: aidocs/platform/47 §2 (PayloadKind SPI), aidocs/platform/83 (T1/SHACL templates), aidocs/platform/87 (TS-CORE-SCHEMA-01 shepardId), aidocs/16 PLUGIN-TABLES-AUDIT-2026-05-24-001 / -003 / -004, aidocs/16 POSTGRES-MULTITENANT-SCHEMA-DECISION, aidocs/16 PG-COLLAPSE-001
---

# 111 — TB1: TableContainer plugin (`shepard-plugin-tables`) design

> **Status.** Design only. No implementation in this PR.
> **Authoritative ledger for TB1.** This doc consolidates the two memory files
> (`project_table_container.md`, `casestudy_table_container.md`), the aidocs/40
> §140 catalog entry, and the three open backlog rows
> (PLUGIN-TABLES-AUDIT-2026-05-24-001 / -003 / -004). Those rows now point
> here; this doc is the SSOT.
> **Out-of-scope.** Cross-collection joins (v2), write SQL credentials (never —
> writes go through the API), Teable embedding (AGPL-3.0 incompatible — see §2),
> formula / computed fields beyond Postgres `GENERATED ALWAYS AS` (v2).

---

## §1 Mission

`shepard-plugin-tables` ships **TableContainer** — the third first-class
payload kind alongside Timeseries and File. A TableContainer is a typed,
schema'd table of rows persisted as a **real Postgres table** in the same
PostgreSQL instance that already hosts the TimescaleDB timeseries
substrate. The killer differentiator is **SQL joinability**: a researcher
can `JOIN` a TableContainer row against a TimescaleDB measurement on the
channel's `shepardId` (TS-CORE-SCHEMA-01, shipped 2026-05-26) in a single
query, without a federation layer, an ETL pipeline, or any custom adapter.

The audience is the **MFFD/LUMEN researcher who today maintains a
600-row Excel master sheet** mapping test runs to inspectors, propellant
batches, ambient conditions, NCR dispositions, and rework chains. That
sheet is the "semantic layer" for their entire dataset. Today shepard
forces them to choose between (a) freetext annotations (no join, no
constraint, no SQL), (b) StructuredDataContainer (Mongo, no SQL, no
columnar query) or (c) keeping the spreadsheet alongside shepard. TB1
gives them the fourth choice: **a table that lives in shepard's data
plane, joins natively with their TS channels, and exposes both REST and
read-only SQL surfaces**. Excel exports become an `INSERT ... SELECT`,
Grafana dashboards become one PostgreSQL datasource, and Jupyter clients
get plain `psycopg2` against a permissioned schema.

Why now: the Postgres + PgBouncer collapse (#77, shipped) and
TS-CORE-SCHEMA-01 (shipped 2026-05-26) are the prerequisites. Without
the shared PG instance, "JOIN against TS data" requires FDW or
federation; without `shepardId` as a stable channel handle, the join key
is a 5-tuple. Both are now in place, so TB1's value proposition becomes
achievable rather than aspirational.

---

## §2 Reuse survey (per CLAUDE.md "Reuse before reimplement")

| Capability TB1 needs | What already exists | Status / fit |
|---|---|---|
| Postgres instance + PgBouncer pool | `infrastructure/docker-compose.yml` `shepard-postgres` (TimescaleDB image), PgBouncer collapse #77 shipped | **Reuse as-is.** TB1 adds a Postgres schema (`shepard_tables`) inside the existing instance. No new container. PG-COLLAPSE-001 (queued) will fold spatial in too; TB1 is one of the three justifying that decision. |
| Flyway migrations against shepard's Postgres | `backend/src/main/resources/db/migration/V1.*.sql` (V1.0.0 → V1.18.1 shipped) | **Reuse.** Plugin ships migrations under `plugins/tables/src/main/resources/db/migration/` and registers them via `PayloadKind.migrations()` (SPI §2.2). Existing `MigrationsRunner` (post-A1e) picks them up; same fail-fast posture. |
| PayloadKind SPI for plugins | `aidocs/47 §2`; `de.dlr.shepard.spi.payload.PayloadKind`; PL1c (HDF5) is the canonical reference impl | **Reuse.** TB1 ships `TablePayloadKind implements PayloadKind` with `name() = "table"`. Discovery is `ServiceLoader.load(PayloadKind.class)` — same as `HdfPayloadKind`. Plugin manifest follows `HdfPluginManifest` shape; runtime toggle is `shepard.plugins.tables.enabled` (default true). |
| Admin-configurable runtime config | A3b `:FeatureToggleRegistry`, N1c2 `:SemanticConfig`, UH1a `:UnhideConfig`. Pattern documented in CLAUDE.md "Always: surface operator knobs in the admin config" | **Reuse pattern.** TB1 ships `:TableContainerConfig` singleton; admin endpoints under `/v2/admin/tables/config`; CLI parity under `shepard-admin tables {status,enable,disable,set-*}` (L1 baseline). |
| Templates as the schema-definition substrate | T1a / T1b / T1c shipped, T1e in-flight; `ShepardTemplate` Neo4j entity; SHACL shape per-template (TPL2 family) | **Reuse — central.** TableContainer schema IS a `ShepardTemplate` with `kind = TABLE_SCHEMA`. Columns are template fields; constraints (NOT NULL, UNIQUE, FK) are SHACL shapes. The template-driven creation rule (CLAUDE.md "Always: every reference type ships a complete create + edit + delete UI", template-system-integration clause) applies directly. See §4. |
| Read-only SQL validator | `SemanticSparqlRest` SPARQL SELECT/ASK gate (the only SPARQL forms permitted, `validateSelectOrAsk()`); pattern: parse query → reject mutation forms → forward to substrate | **Reuse pattern.** TB1's `/v2/table-containers/{appId}/sql` mirrors this: parse query → reject non-`SELECT` → run against a read-only Postgres role bound to the caller's permitted schemas. The SPARQL validator is the precedent. |
| TimescaleDB JOIN compatibility | TS-CORE-SCHEMA-01 shipped 2026-05-26 — `shepard_ts.timeseries.shepard_id UUID NOT NULL UNIQUE`; `shepard_ts.measurements` hypertable carries the channel id (post-rename); `TsChannelResolver` JDBC service | **Reuse.** A TableContainer column of type `RELATION_CHANNEL` is a `UUID` column with a foreign-key to `shepard_ts.timeseries(shepard_id)`. JOIN queries work directly. No FDW, no view, no proxy. |
| Per-Collection permissions model | `PermissionsService`, Neo4j `:HAS_READER` / `:HAS_WRITER` / `:HAS_MANAGER` edges, `JWTFilter` enforces at REST entry | **Reuse for REST.** REST endpoints inherit the parent Collection's permission gate. For SQL: schema-per-Collection + role-per-Collection (§6). |
| Teable | Teable (teableio/teable) is **AGPL-3.0** — incompatible with the Apache-2.0 fork policy per CLAUDE.md dependency-review rule. **No code reuse.** | **Inspiration only — visual reference + spreadsheet UX patterns.** TB1 builds the same shape (table = real Postgres table) natively. The decision to NOT embed Teable was confirmed 2026-05-20 (`casestudy_table_container.md`). |
| TanStack Table for frontend | Vuetify 3 ecosystem; `frontend/components/` does not yet ship a power-user data grid. TanStack Table is MIT. | **Adopt as the frontend grid.** Same library Vuetify-native projects use; supports virtualisation, column resize, multi-sort, filtering — none of which the existing Vuetify `v-data-table` does well at 10k rows. See §8. |
| Apache Iceberg / sqlite-vec (web research) | Iceberg solves "many-table catalog" at scale (PB-class); shepard's per-tenant scale is GB-class — out of scope. sqlite-vec solves vector search in SQLite; not relevant. | **Considered, rejected** for v1. Iceberg is the future when shepard becomes a federated lake; TB1 v1 targets <10M rows per table, <1k tables per instance — plain Postgres is the right fit. |
| Postgres FDW | postgres_fdw could federate across PG instances; we're collapsing instances instead (PG-COLLAPSE-001) | **Not needed for v1** — single instance + schemas. FDW becomes interesting only if a customer requires substrate isolation. |
| Postgres RLS (row-level security) | Native to Postgres 16+ in the shipped image | **Adopt for SQL surface.** §6 enables RLS on `shepard_tables.<table>` to enforce per-Collection visibility for SQL clients. REST already enforces at filter layer. |

**Net survey conclusion.** TB1 ships *almost no new infrastructure*. The
PayloadKind SPI is the boilerplate seam (PL1c proved the shape with
HDF5); Postgres is already in the stack; templates already exist; the
SPARQL validator pattern translates 1:1 to the SQL gate. The novel
work is the schema-to-DDL translator (column type → SQL type + check
constraints), the per-Collection RLS bootstrap, and the frontend grid.

---

## §3 Wire shapes

All endpoints land under `/v2/table-containers/`. Per CLAUDE.md
"Always: UI never asks for paths/URLs — pulls from references" and the
appId-first rule, every identifier is a `shepardId` (UUID v7, the
`appId` Java field). The plugin's REST layer follows `HdfReferenceRest`
/ `StructuredDataContainerRest` conventions; OpenAPI is auto-emitted.

### §3.1 Create a TableContainer

```http
POST /v2/table-containers
Content-Type: application/json
Authorization: Bearer <jwt>

{
  "name": "tr-instruments",
  "parentCollectionAppId": "019e7243-f995-7914-be80-53e367aa5172",
  "templateAppId": "019e72a1-0001-7c10-b001-4f2a3c000001",
  "description": "Instrument inventory for LUMEN test campaign 2026."
}
```

**Response 201:**

```json
{
  "appId": "019e72b3-3300-7c50-9c01-7a8d9f000002",
  "name": "tr-instruments",
  "physicalTableName": "shepard_c_lumen_2026.tr_instruments",
  "parentCollectionAppId": "019e7243-f995-7914-be80-53e367aa5172",
  "templateAppId": "019e72a1-0001-7c10-b001-4f2a3c000001",
  "rowCount": 0,
  "schemaVersion": 1,
  "created": "2026-05-31T09:14:22Z",
  "createdBy": "fkrebs"
}
```

**Side effects:**
- Mints `appId` (UUID v7 per `AppIdGenerator.next()`).
- Slugifies `name` → `tr_instruments` (lowercase, `[a-z0-9_]+`, max 48
  chars; collisions append `_2`, `_3`, …).
- Reads the `ShepardTemplate` at `templateAppId`, materialises its
  `kind = TABLE_SCHEMA` fields into a `CREATE TABLE
  shepard_c_<collection-slug>.<table-slug> (...)` statement, executes
  it (Flyway-style migration recorded in
  `shepard_tables_meta.schema_evolution`).
- Wires PROV-O `:Activity` per CLAUDE.md "audit trail is a graph"; the
  filter is bypassed via `PROP_SKIP_CAPTURE` because this handler
  records the schema-version Activity directly.

### §3.2 Append rows

```http
POST /v2/table-containers/019e72b3-3300-7c50-9c01-7a8d9f000002/rows
Content-Type: application/json
Authorization: Bearer <jwt>
X-AI-Agent: claude-opus-4-7      # optional — drives sourceMode = "ai"

{
  "rows": [
    {
      "instrument_id": "AFP-1",
      "kind": "AFP-robot",
      "calibration_due": "2026-08-01",
      "channel_temperature": "019e72cc-aa01-7c10-b001-4f2a3c000abc",
      "owner_person_id": "fkrebs"
    },
    {
      "instrument_id": "AFP-2",
      "kind": "AFP-robot",
      "calibration_due": "2026-09-15",
      "channel_temperature": "019e72cc-aa01-7c10-b001-4f2a3c000abd",
      "owner_person_id": "mschmidt"
    }
  ],
  "onConflict": "ERROR"
}
```

**Response 201:**

```json
{
  "inserted": 2,
  "rowAppIds": [
    "019e72e0-0001-7c60-a002-8b9d0f000010",
    "019e72e0-0001-7c60-a002-8b9d0f000011"
  ],
  "activityAppId": "019e72e0-0001-7c60-a002-8b9d0f0000ff"
}
```

- `onConflict ∈ {ERROR, SKIP, UPSERT_ON_PRIMARY_KEY}`. Default ERROR
  surfaces a 409 with the conflicting row's primary key.
- Every row carries an `appId` UUID v7 column (`shepard_id`),
  auto-minted server-side; the client never supplies it.
- `X-AI-Agent` header presence sets the Activity's `sourceMode = "ai"`
  (CLAUDE.md cross-cutting-headers rule + EU AI Act Art. 50).

### §3.3 Query rows (REST)

```http
GET /v2/table-containers/019e72b3-.../rows
    ?filter=kind:eq:AFP-robot,calibration_due:lt:2026-09-01
    &select=instrument_id,kind,calibration_due
    &sort=calibration_due:asc
    &limit=100
    &offset=0
```

**Response 200:**

```json
{
  "rows": [
    {
      "instrument_id": "AFP-1",
      "kind": "AFP-robot",
      "calibration_due": "2026-08-01"
    }
  ],
  "totalCount": 1,
  "nextOffset": null
}
```

- Filter grammar: `field:op:value[,field:op:value...]` with `op ∈ {eq,
  ne, lt, le, gt, ge, in, like, isnull, notnull}`. AND-combined; OR
  requires the SQL surface (§3.5).
- `select` is optional; omitting it returns every column.
- `totalCount` requires a second COUNT query — gated by
  `shepard.tables.return-total-count` (default true, can be disabled
  for >1M-row tables).

### §3.4 Patch / delete row

```http
PATCH /v2/table-containers/{tableAppId}/rows/{rowAppId}
Content-Type: application/merge-patch+json
{ "calibration_due": "2026-08-15" }
```

```http
DELETE /v2/table-containers/{tableAppId}/rows/{rowAppId}
```

Both record a typed `:RowMutationActivity` (subtype of `:Activity`)
with before/after column values for PATCH (CLAUDE.md audit-graph rule).

### §3.5 Read-only SQL

```http
POST /v2/table-containers/019e72b3-.../sql
Content-Type: application/json

{
  "query": "SELECT i.instrument_id, AVG(m.value) AS avg_temp_c \
            FROM tr_instruments i \
            JOIN shepard_ts.measurements m \
                 ON m.channel_shepard_id = i.channel_temperature \
            WHERE m.ts BETWEEN '2026-05-30 09:00:00' \
                           AND '2026-05-30 11:00:00' \
            GROUP BY i.instrument_id \
            ORDER BY avg_temp_c DESC \
            LIMIT 20;",
  "format": "json"
}
```

**Response 200:** `application/sql-results+json` (TB1 minted; mirror of
W3C SPARQL Results JSON):

```json
{
  "head": { "vars": ["instrument_id", "avg_temp_c"] },
  "results": {
    "bindings": [
      { "instrument_id": {"type":"text","value":"AFP-1"},
        "avg_temp_c":   {"type":"numeric","value":"187.42"} },
      { "instrument_id": {"type":"text","value":"AFP-2"},
        "avg_temp_c":   {"type":"numeric","value":"183.10"} }
    ]
  }
}
```

**Validator gate** (mirrors `SemanticSparqlRest`):

1. Parse the query with the embedded `jsqlparser` SQL parser.
2. Reject if the AST contains anything other than `SELECT` /
   `WITH ... SELECT`. Forms rejected with **400**: `INSERT`, `UPDATE`,
   `DELETE`, `CREATE`, `ALTER`, `DROP`, `GRANT`, `REVOKE`, `COPY`,
   `CALL`, `TRUNCATE`, `LOCK`, `VACUUM`, `ANALYZE`, `EXPLAIN ANALYZE`,
   `SET`, `RESET`, `SHOW`, `LISTEN`, `NOTIFY`, function bodies (DO
   blocks).
3. Reject if any referenced table is outside the caller's permitted
   schemas. The permitted set is computed from the caller's Collection
   roles (`shepard_c_<colSlug>` for every readable Collection, plus the
   shared `shepard_ts` and `shepard_meta`).
4. Open a Postgres connection under the per-Collection read-only role
   `shepard_r_<collectionAppId>`. Postgres RLS plus role-grants give
   defence in depth — even if the validator misses a form, RLS denies.
5. Enforce per-query timeout (`shepard.tables.sql.query-timeout`,
   default 30s) and per-query row cap
   (`shepard.tables.sql.max-rows`, default 100_000) via Postgres
   `statement_timeout` + cursor-LIMIT wrap.
6. Stream the result set; close cursor; emit a `:SqlQueryActivity`
   capturing the query text + caller + duration + row count.

### §3.6 Schema endpoints

```http
GET /v2/table-containers/{appId}/schema
```

**Response 200:**

```json
{
  "tableAppId": "019e72b3-...",
  "physicalTableName": "shepard_c_lumen_2026.tr_instruments",
  "templateAppId": "019e72a1-...",
  "schemaVersion": 1,
  "columns": [
    { "name": "instrument_id",   "type": "TEXT",       "primaryKey": true,
      "nullable": false, "unique": true,  "description": "ZLP asset tag" },
    { "name": "kind",            "type": "SINGLE_SELECT",
      "nullable": false, "options": ["AFP-robot","CRW-head","spot-welder","LBR-iiwa"] },
    { "name": "calibration_due", "type": "DATE",       "nullable": true },
    { "name": "channel_temperature", "type": "RELATION_CHANNEL",
      "fkTable": "shepard_ts.timeseries", "fkColumn": "shepard_id",
      "nullable": true },
    { "name": "owner_person_id", "type": "RELATION_DATAOBJECT",
      "fkLabel": "Person", "nullable": true }
  ],
  "constraints": [
    { "kind": "PRIMARY_KEY", "columns": ["instrument_id"] },
    { "kind": "CHECK", "name": "calib_in_future",
      "expr": "calibration_due IS NULL OR calibration_due >= CURRENT_DATE - INTERVAL '90 days'" }
  ]
}
```

```http
PATCH /v2/table-containers/{appId}/schema
Content-Type: application/json
{
  "addColumns": [
    { "name": "rework_count", "type": "INTEGER", "nullable": true, "default": 0 }
  ],
  "addConstraints": [
    { "kind": "CHECK", "name": "rework_nonneg", "expr": "rework_count >= 0" }
  ]
}
```

**Schema-evolution policy (additive only via PATCH):**

- `addColumns` — always permitted; emits `ALTER TABLE ... ADD COLUMN
  IF NOT EXISTS` with default; bumps `schemaVersion`. New columns must
  be nullable OR carry a default (CLAUDE.md "Always: schema changes
  are additive and nullable").
- `addConstraints` — permitted if the constraint validates against
  existing rows (`ALTER TABLE ... ADD CONSTRAINT ... NOT VALID`,
  optional `VALIDATE CONSTRAINT` admin job).
- **Drop / rename columns / drop constraints are NOT on this endpoint.**
  They land on a separate admin-only destructive endpoint
  `POST /v2/admin/table-containers/{appId}/schema/destructive-mutation`
  (§3.8) so the loud audit trail is unmistakable.

### §3.7 Admin config singleton (`:TableContainerConfig`)

Per CLAUDE.md "Always: surface operator knobs in the admin config":

```http
GET /v2/admin/tables/config
```

```json
{
  "enabled": true,
  "sqlSurfaceEnabled": true,
  "sqlQueryTimeoutSec": 30,
  "sqlMaxRowsPerQuery": 100000,
  "sqlConcurrentQueriesPerCollection": 4,
  "restReturnTotalCount": true,
  "restMaxRowsPerInsert": 10000,
  "schemaEvolutionRequiresAdmin": false,
  "rowLevelSecurity": "ENFORCED",
  "tableNameMaxLength": 48,
  "perCollectionPgPoolSize": 8
}
```

```http
PATCH /v2/admin/tables/config
Content-Type: application/merge-patch+json
{ "sqlQueryTimeoutSec": 60, "sqlSurfaceEnabled": false }
```

`@RolesAllowed("instance-admin")`. Mutations land in `:Activity`; CLI
parity under `shepard-admin tables {status,enable,disable,set-sql-timeout
<sec>,set-sql-max-rows <n>,...}` with the L1 baseline flags.

### §3.8 Destructive schema mutation (admin only)

```http
POST /v2/admin/table-containers/{appId}/schema/destructive-mutation
{
  "operation": "DROP_COLUMN",
  "columnName": "deprecated_field",
  "rollbackPlan": "shadow_table_swap | drop_in_place",
  "ackDataLoss": true
}
```

Required: `instance-admin` role, `ackDataLoss: true`, and (for
non-empty tables) a prior backup confirmation token from
`POST /v2/admin/table-containers/{appId}/backup-snapshot`. The shadow-
table-swap pattern (CREATE replacement → COPY → ATOMIC RENAME) is the
default; drop-in-place is only allowed when row count is 0.

### §3.9 Error envelope

RFC 7807 problem+json (consistent with H4 rollout):

```json
{
  "type": "https://shepard.example/problems/table/invalid-sql-form",
  "title": "Only SELECT queries are permitted on the SQL surface",
  "status": 400,
  "detail": "Encountered INSERT at line 1, column 1. The SQL surface is read-only.",
  "instance": "/v2/table-containers/019e72b3-.../sql"
}
```

---

## §4 Schema as template (T1 family integration)

This is the central design choice. A TableContainer's schema is not a
bespoke data structure — it IS a `ShepardTemplate` instance with
`kind = TABLE_SCHEMA`. The same template machinery that drives
form-generation for any other entity drives the column set for a table.

**Template shape:**

```turtle
@prefix sh:       <http://www.w3.org/ns/shacl#> .
@prefix shepard:  <urn:shepard:> .
@prefix xsd:      <http://www.w3.org/2001/XMLSchema#> .

shepard:tpl_lumen_instruments a shepard:TableSchemaTemplate ;
    shepard:name "Instrument inventory (LUMEN)" ;
    shepard:appId "019e72a1-0001-7c10-b001-4f2a3c000001" ;
    sh:property [
        sh:path shepard:col_instrument_id ;
        shepard:columnName "instrument_id" ;
        shepard:columnType "TEXT" ;
        sh:minCount 1 ; sh:maxCount 1 ;
        sh:datatype xsd:string ;
        shepard:isPrimaryKey true ;
        sh:description "ZLP asset tag" ;
    ] ;
    sh:property [
        sh:path shepard:col_kind ;
        shepard:columnName "kind" ;
        shepard:columnType "SINGLE_SELECT" ;
        sh:in ("AFP-robot" "CRW-head" "spot-welder" "LBR-iiwa") ;
        sh:minCount 1 ;
    ] ;
    sh:property [
        sh:path shepard:col_channel_temperature ;
        shepard:columnName "channel_temperature" ;
        shepard:columnType "RELATION_CHANNEL" ;
        shepard:fkTable "shepard_ts.timeseries" ;
        shepard:fkColumn "shepard_id" ;
    ] .
```

**Column-type ↔ Postgres-type ↔ SHACL-shape map (v1):**

| TB1 column type | Postgres type | SHACL shape | Notes |
|---|---|---|---|
| `TEXT` | `TEXT` | `sh:datatype xsd:string` | Free-text. |
| `INTEGER` | `BIGINT` | `sh:datatype xsd:integer` | 64-bit signed. |
| `NUMBER` | `DOUBLE PRECISION` | `sh:datatype xsd:double` | IEEE 754 64-bit. |
| `NUMERIC(p,s)` | `NUMERIC(p,s)` | `sh:datatype xsd:decimal` + `sh:totalDigits` + `sh:fractionDigits` | Fixed precision (currency, lab readings). |
| `BOOLEAN` | `BOOLEAN` | `sh:datatype xsd:boolean` | |
| `DATE` | `DATE` | `sh:datatype xsd:date` | |
| `TIMESTAMP` | `TIMESTAMPTZ` | `sh:datatype xsd:dateTime` | Always UTC on wire. |
| `SINGLE_SELECT` | `TEXT` + `CHECK col IN (...)` | `sh:in ( ... )` | Static enum baked at schema-create. |
| `MULTI_SELECT` | `TEXT[]` + per-element CHECK | `sh:in` over node-shape | Postgres array. |
| `JSON` | `JSONB` | `shepard:columnType "JSON"` | Free schema for compatibility with imported sheets. |
| `RELATION_CHANNEL` | `UUID` + FK to `shepard_ts.timeseries(shepard_id)` | `sh:nodeKind sh:IRI` + `sh:class shepard:TimeseriesChannel` | The TS-join enabler. |
| `RELATION_DATAOBJECT` | `UUID` + FK to `shepard_meta.dataobject_appid_index(appid)` | `sh:nodeKind sh:IRI` + `sh:class` per Neo4j label | Plugin maintains the index table (`appid → neo4j-id`) as a materialised mapping for FK enforcement. |
| `RELATION_TABLEROW` | `UUID` + FK to `<other-table>.shepard_id` | `sh:class` + `shepard:fkTable` | Cross-table joins inside the same Collection schema. |

**Constraints as SHACL:** `sh:minCount = 1` ↔ `NOT NULL`; `sh:in` ↔
`CHECK col IN (...)`; `sh:pattern` ↔ `CHECK col ~ '<regex>'`;
`sh:minInclusive` / `sh:maxInclusive` ↔ `CHECK col BETWEEN ... AND ...`;
`sh:unique = true` (custom predicate) ↔ `UNIQUE(col)`.

**Governance dividend.** Because the schema is a template:
- The same SHACL validator that gates other Shepard writes gates table
  inserts — one validator, one error format.
- A schema's history lives in the template's `:PV` chain (PV1 family,
  shipped) — every schema edit is a diff against the template's previous
  version.
- Templates are sharable and findable in the existing template browser
  (T1c) — a researcher can "use the LUMEN instrument template as a
  starting point" without copy-paste.
- The frontend Create dialog (§8) gets template-driven autocomplete + a
  pre-validated form for free.

**Template-driven row creation (T1e parity).** Per CLAUDE.md "Always:
every reference type ships a complete create + edit + delete UI"
template clause: if a parent DataObject has a `ShepardTemplate` that
declares a `TableContainer` with pre-filled defaults (e.g. "every test
run gets an 'inspections' table seeded with one row per inspection
gate"), the TableContainer creation flow MUST pre-fill those rows.
TB1 ships this from day one, not as a follow-up.

---

## §5 TS joins — the differentiator

This is the value proposition that justifies TB1's existence over
"keep your Excel sheet." A TableContainer column of type
`RELATION_CHANNEL` is a `UUID` foreign-key into
`shepard_ts.timeseries(shepard_id)` — the column added by
`V1.11.0__add_shepard_id_to_timeseries.sql` and now the canonical
channel identifier post-TS-CORE-SCHEMA-01.

### §5.1 The single-instance shape

```
shepard-postgres (TimescaleDB image, PG 16+)
├── schema shepard_ts
│   ├── timeseries           (channel metadata, shepard_id UUID UNIQUE)
│   └── measurements         (hypertable; channel_shepard_id UUID FK → timeseries.shepard_id)
├── schema shepard_meta
│   └── dataobject_appid_index   (UUID → neo4j-id mapping for FK enforcement)
├── schema shepard_c_<collection-slug>     (one per Collection — RLS-gated)
│   ├── tr_instruments
│   ├── tr_inspections
│   └── tr_calibration_certs
└── schema shepard_tables_meta
    ├── schema_evolution     (DDL history; immutable append-only)
    └── slug_registry        (collection-slug ↔ collection-appid ↔ schema-name)
```

### §5.2 The join in SQL

The example from §3.5 is the killer query — instrument inventory
joined to live TS measurements over a time window, grouped per
instrument. Today this requires (a) Excel export, (b) manual channel
ID lookup, (c) Influx/Timescale query, (d) Pandas merge. TB1 makes
it one query.

### §5.3 The join in REST

The same query is also expressible through a REST-side join helper
that prepares a typed result:

```http
POST /v2/table-containers/{tableAppId}/join-ts
{
  "tableColumns": ["instrument_id"],
  "joinOn":   "channel_temperature",
  "tsWindow": { "from": "2026-05-30T09:00Z", "to": "2026-05-30T11:00Z" },
  "tsAggregation": "avg",
  "tsResultColumnName": "avg_temp_c"
}
```

Returns the same shape as `/rows` so existing UI components can
render it. This is the "no SQL knowledge required" affordance for
casual users — the SQL surface is for power users; this is the
template-driven equivalent.

### §5.4 Cross-table joins inside a Collection

Tables in the same Collection share a Postgres schema and can join
freely (the SQL validator only restricts the `FROM` table set to
permitted schemas; within a permitted schema all joins work).

### §5.5 Cross-Collection joins (v2, NOT v1)

A researcher with read access to two Collections can in principle
write a SQL query joining `shepard_c_A.t1` JOIN `shepard_c_B.t2`. v1
keeps this **disabled** by default — the validator's permitted-schema
list contains only the *current* Collection (passed as a `?collection=`
query param). v2 ships an `allowCrossCollection: true` flag on
`:TableContainerConfig` + per-query schema-set expansion. The
intentional friction in v1 prevents accidental data-mixing across
permission boundaries.

---

## §6 Permissions

The TB1 permission surface is **two distinct paths** — REST inherits
Shepard's existing Neo4j-edge model; SQL gets a Postgres-native
defence-in-depth model. Both are bound to the same source of truth:
the Collection's `:HAS_READER` / `:HAS_WRITER` / `:HAS_MANAGER` edges.

### §6.1 REST gate

Every endpoint reads the table's `parentCollectionAppId` (a column on
the TableContainer Neo4j entity and a column on every row's
`shepard_meta` projection) and runs:

```java
permissionsService.requireRead(caller, parentCollectionAppId);   // GET *, /sql, /schema
permissionsService.requireWrite(caller, parentCollectionAppId);  // POST /rows, PATCH /rows/*
permissionsService.requireManage(caller, parentCollectionAppId); // PATCH /schema
```

Exactly the pattern `HdfReferenceRest` and `StructuredDataContainerRest`
use today.

### §6.2 SQL gate — schema isolation + scoped credentials

The SQL surface (§3.5) bypasses the JAX-RS filter chain in the sense
that the Postgres connection is opened directly. Defence in depth:

1. **Schema isolation.** Each Collection gets a dedicated Postgres
   schema `shepard_c_<collection-slug>`. Tables for that Collection
   live there. The shared `shepard_ts.measurements` hypertable is
   readable by all because TS data is already gated at the Reference
   level.
2. **Role-per-Collection.** A Postgres role `shepard_r_<collection-appid>`
   gets `USAGE` on its own schema + `SELECT` on every table in it.
   No GRANT on other Collection schemas. Created by a Flyway hook
   when a Collection is created; revoked on Collection delete.
3. **JWT-as-Postgres-password.** Postgres 16+ validates JWT signature
   against Keycloak's JWKS endpoint via the `pgjwt` extension.
   `psql --user=fkrebs --password=<jwt>` works directly. JWT expiry
   = automatic credential rotation; Keycloak revoke is immediate.
4. **Row-Level Security (RLS).** `ALTER TABLE shepard_ts.measurements
   ENABLE ROW LEVEL SECURITY`; policy: `channel_shepard_id IN (SELECT
   shepard_id FROM shepard_ts.timeseries WHERE container_appid IN
   <readable-container-set>)`. The `<readable-container-set>` resolves
   via a session variable set by the connection wrapper using the JWT
   sub claim. This means even a raw `psql` against `shepard_ts`
   returns only the TS rows the user can read in the REST API.
5. **Per-Collection PgBouncer pool.** Limits a runaway query in one
   Collection from starving others.

### §6.3 Service-account credentials for tools

Grafana / Superset / pipelines need long-lived SQL credentials.
Per `casestudy_table_container.md`:

```http
POST /v2/collections/{appId}/sql-credentials
{
  "purpose": "grafana-monitoring",
  "ttl": "P90D"
}
```

Returns `{client_id, client_secret}` — a Keycloak service account.
The client uses Keycloak's client_credentials flow to mint JWTs;
each minted JWT becomes a Postgres password. Revoke = delete the
Keycloak service account. Every mint logged as a `:Activity`.

### §6.4 Writes never go through SQL

The SQL surface is **strictly read-only**. Writes route through the
REST API where the JAX-RS filter chain, SHACL validator, schema-version
guard, and Activity capture all run. This isn't laziness — it's the
seam that keeps the audit trail intact (CLAUDE.md "the audit trail is a
graph"). A write through psql wouldn't trigger SHACL, wouldn't emit
PROV-O, wouldn't bump the schema-evolution counter.

---

## §7 Plugin shape

Module structure under `plugins/tables/` (sibling to `plugins/hdf5/`):

```
plugins/tables/
├── pom.xml                                         # mirrors plugins/hdf5/pom.xml
├── docs/
│   ├── reference.md                                # power-user / operator reference
│   ├── quickstart.md                               # casual-task pages
│   └── install.md                                  # operator install guide
├── compose-profile.yml                             # EMPTY (no sidecar; PG is core)
└── src/
    ├── main/
    │   ├── java/de/dlr/shepard/
    │   │   ├── plugins/tables/
    │   │   │   ├── TablePayloadKind.java           # ServiceLoader-registered SPI impl
    │   │   │   ├── TablePluginManifest.java        # PluginManifest SPI impl
    │   │   │   └── TableSidecars.java              # returns List.of() — no sidecar
    │   │   ├── data/tables/
    │   │   │   ├── entities/
    │   │   │   │   ├── TableContainer.java         # Neo4j @NodeEntity, HasAppId
    │   │   │   │   ├── TableSchemaVersion.java     # immutable schema-evolution row
    │   │   │   │   └── TableRowProvenance.java     # bridge node for per-row PROV (optional v2)
    │   │   │   ├── daos/
    │   │   │   │   ├── TableContainerDAO.java      # Neo4j-OGM CRUD
    │   │   │   │   └── TableRowDAO.java            # JDBC against the Postgres table
    │   │   │   ├── services/
    │   │   │   │   ├── TableDdlService.java        # template → CREATE TABLE
    │   │   │   │   ├── TableSchemaEvolutionService.java
    │   │   │   │   ├── TableRowQueryService.java   # filter grammar → parameterised SQL
    │   │   │   │   ├── TableSqlValidator.java      # jsqlparser SELECT-only gate
    │   │   │   │   ├── TableSqlExecutor.java       # opens role-bound connection, streams
    │   │   │   │   ├── TableTsJoinService.java     # REST join helper (§5.3)
    │   │   │   │   └── TablePermissionBootstrap.java # creates per-Collection role + RLS
    │   │   │   ├── io/                             # request/response records
    │   │   │   │   ├── TableContainerIO.java
    │   │   │   │   ├── TableRowsIO.java
    │   │   │   │   ├── TableSchemaIO.java
    │   │   │   │   ├── TableSqlRequestIO.java
    │   │   │   │   └── TableSqlResultIO.java       # SPARQL-Results-JSON-shaped
    │   │   │   └── permissions/
    │   │   │       └── TablePermissionBridge.java  # collection-grant ↔ pg-role sync
    │   │   ├── v2/tables/resources/
    │   │   │   ├── TableContainerRest.java         # /v2/table-containers/*
    │   │   │   ├── TableRowsRest.java
    │   │   │   ├── TableSchemaRest.java
    │   │   │   ├── TableSqlRest.java
    │   │   │   └── TableTsJoinRest.java
    │   │   ├── v2/admin/tables/
    │   │   │   ├── TableAdminRest.java             # /v2/admin/tables/config + destructive ops
    │   │   │   └── io/TableConfigIO.java
    │   │   └── context/tables/
    │   │       ├── entities/TableContainerConfig.java   # the runtime singleton
    │   │       └── services/TableConfigService.java
    │   └── resources/
    │       ├── META-INF/services/
    │       │   ├── de.dlr.shepard.spi.payload.PayloadKind
    │       │   └── de.dlr.shepard.plugin.PluginManifest
    │       ├── db/migration/
    │       │   ├── V2.0.0__tables_create_meta_schemas.sql          # shepard_meta, shepard_tables_meta
    │       │   ├── V2.0.1__tables_install_pgjwt_extension.sql      # JWT auth for SQL surface
    │       │   ├── V2.0.2__tables_create_dataobject_appid_index.sql
    │       │   ├── V2.0.3__tables_create_slug_registry.sql
    │       │   └── V2.0.4__tables_create_schema_evolution_log.sql
    │       └── neo4j/migration/
    │           └── V61__tables_appid_constraint.cypher
    └── test/
        ├── java/de/dlr/shepard/plugins/tables/     # SPI tests
        └── java/de/dlr/shepard/data/tables/        # service tests + DDL translator tests
```

**Discovery / lifecycle.** `META-INF/services/de.dlr.shepard.spi.payload.PayloadKind`
points at `TablePayloadKind`; `META-INF/services/de.dlr.shepard.plugin.PluginManifest`
points at `TablePluginManifest`. Runtime toggle:
`shepard.plugins.tables.enabled` (default `true`) and
`shepard.tables.enabled` (default `true` — the master switch, mirrors
HDF5 `shepard.hdf.enabled`).

**Sidecars.** `TableSidecars.sidecars()` returns `List.of()` — Postgres
is already a core dependency, no new container.

**Build / classpath.** Same `provided`-scope `backend` dependency
(self-exclusion list) as HDF5; same two-pass build order (`backend
-DnoPlugins install` → `plugins/tables install` → `backend package`).

**Three-pane docs.** Per CLAUDE.md "Always: plugins ship their own
documentation":
- `plugins/tables/docs/reference.md` — every endpoint, every config
  key, every column type, the SQL validator gate, RLS rules. Power-user
  doc.
- `plugins/tables/docs/quickstart.md` — task pages:
  "I have an Excel sheet, how do I import it?", "I want a Grafana
  dashboard joining instruments and TS data, how?", "How do I add a
  column to an existing table?"
- `plugins/tables/docs/install.md` — config keys, Postgres extension
  prerequisites (`pgjwt`), Flyway-migration ordering, per-Collection
  role / RLS bootstrap, troubleshooting.

---

## §8 Frontend integration

### §8.1 Mount point

The parent DataObject detail page
(`frontend/pages/collections/[collectionId]/dataobjects/[dataObjectId]/index.vue`)
gets a new "Tables" pane in the reference-list panel, listing every
`TableContainer` linked to this DataObject. Each row is "name, column
count, row count, last-touched, [Open]". The pane is rendered by
`<TableContainerListPane>`.

### §8.2 The table detail view

`TableContainerPane.vue` mounts at
`/collections/[collectionId]/table-containers/[tableAppId]`.
Three sub-tabs:

1. **Data** — the grid (TanStack Table v8 + Vuetify styling).
   Server-side pagination (REST `/rows` with `limit`/`offset`).
   Column filters → REST `filter=` grammar.
   Column sort → REST `sort=` param.
   Inline edit → REST PATCH on the row's `appId`.
   Bulk import → CSV/XLSX drop-zone → REST POST `/rows` in batches of
   `restMaxRowsPerInsert` (default 10000).
   At 4K viewport (per CLAUDE.md `feedback_validate_user_viewport.md`),
   the grid fills 95% of available width; row height 48px; sticky
   header + first-column-as-row-anchor.
2. **Schema** — column editor; constraint editor; "Edit underlying
   template" link to T1 template browser. Schema mutations route
   through PATCH `/schema`. Destructive ops surface a banner: "This
   requires admin role — [request via admin]."
3. **SQL** — playground (subset of the SPARQL playground UX). CodeMirror
   editor with SQL syntax + Postgres autocomplete (schema/table/column
   list pulled from `/schema`). Run button posts to `/sql`. Result
   grid renders the JSON envelope. "Cross-collection joins disabled"
   chip when applicable. "Open in Grafana" button copies the query
   to clipboard + an explanatory dialog.

### §8.3 The creation flow

A "Create TableContainer" action from a DataObject opens a two-step
wizard:

1. **Pick template.** Template browser filtered to `kind = TABLE_SCHEMA`.
   "Empty schema" option always present. Selected template populates
   the column list.
2. **Name + bind.** Set human-readable name (slugifies in real time
   with preview), select parent Collection (defaults to the
   DataObject's Collection), set parent-DataObject link. "Create"
   posts to `/v2/table-containers`. On 201, navigates straight to the
   Data tab with a "✓ Created" toast.

The template-driven pre-fill from a parent DataObject's
`ShepardTemplate` happens here per CLAUDE.md
"feedback_template_driven_create_all_refs.md" — if the parent
DataObject's template carries `:hasTableSchemaTemplate`, the wizard
defaults to that template (T1e parity).

### §8.4 The placeholder stub (CLAUDE.md UI-stub rule)

Per CLAUDE.md "Always: ship a UI stub for every backend feature": the
first TB1 backend slice ships with `TableContainerPane.vue` as a
**real functional UI**, not a placeholder. The placeholder kit is
the floor we deliberately exceed because TB1's user-facing surface IS
the value proposition — backend-only TB1 has zero researcher value.

### §8.5 Basic vs advanced mode

Per CLAUDE.md "Always: advanced mode = strict superset of basic":
- Basic mode shows the Data tab + Create wizard. SQL tab and Schema
  editor are hidden behind an "Advanced view" chip.
- Advanced mode shows all three tabs from the start.

---

## §9 MCP integration

Three new MCP tools land under `shepard-plugin-mcp` (the MCP server,
shipped). Filed against `MCP-COV-*` with sub-row `MCP-COV-TB1`:

| Tool | Args | Returns | Notes |
|---|---|---|---|
| `table_get_schema` | `tableAppId` | `TableSchemaIO` | Read-only. Same shape as `/schema`. |
| `table_query` | `tableAppId`, `filter`, `select`, `limit`, `offset` | rows JSON | Same as `/rows`. |
| `table_sql` | `tableAppId`, `query` | SQL-results JSON | Same gate as `/sql`; same per-Collection role. Best-of-three for AI workflows: TS join semantics live here. |
| `table_append_rows` | `tableAppId`, `rows[]`, `onConflict` | inserted count + appIds | Writes; tagged `X-AI-Agent` automatic per MCP shape. |

A Claude agent can resolve `TR-004 → linked TableContainer (NCRs) → query for
status='OPEN' → cross-reference with TS measurements during the anomaly window`
in three MCP calls — the exact shape the 2026-05-30 MCP gap analysis
flagged as missing (`aidocs/agent-findings/api-scrutinizer.md`, MCP gap
list).

The MCP tool surface is **strictly read for the SQL form** (no
`table_sql_write`). Writes go through `table_append_rows`, which
inherits the REST validator chain.

---

## §10 Implementation phases

Each phase = one PR. Phases are sized to ship behind
`shepard.tables.enabled=false` until §10.4 (the first user-facing
slice). Backlog rows for `aidocs/16-dispatcher-backlog.md`:

### TB1a — backend skeleton + meta schema (1 PR, S)

- `plugins/tables/` Maven module scaffolding (mirrors `plugins/hdf5/`).
- `TablePluginManifest`, `TablePayloadKind` (entity packages
  registered).
- Neo4j entity `TableContainer` + DAO + IO.
- Flyway migrations V2.0.0–V2.0.4 (meta schemas, `pgjwt` install,
  appid index, slug registry, schema-evolution log).
- `TableContainerConfig` runtime singleton + bootstrap.
- No REST endpoints yet (or only `GET /v2/admin/tables/config`).
- Tests: SPI registration test (mirrors `HdfPluginManifestTest`), DDL
  service unit tests against testcontainers Postgres.
- Toggle: `shepard.tables.enabled` default `false`. Plugin discoverable
  but inert.

### TB1b — schema-as-template + DDL translator (1 PR, M)

- `kind = TABLE_SCHEMA` added to `ShepardTemplate`.
- `TableDdlService`: template (SHACL shape) → `CREATE TABLE` /
  `ALTER TABLE` statement set.
- Column type ↔ Postgres type map (the §4 table) with unit-tested
  round-trip.
- `POST /v2/table-containers` + `GET /v2/table-containers/{appId}` +
  `GET /v2/table-containers/{appId}/schema` + `PATCH /schema` (additive
  only).
- Schema evolution recorded in `shepard_tables_meta.schema_evolution`
  + emits `:SchemaEvolutionActivity`.
- No row CRUD yet.
- Tests: DDL translator round-trip (every column type), schema-version
  bump correctness, additive-only enforcement.
- Toggle still `false` by default — no user-facing surface.

### TB1c — row CRUD (REST) (1 PR, M)

- `POST /v2/table-containers/{appId}/rows` (batch insert with
  `onConflict`).
- `GET /v2/table-containers/{appId}/rows` (filter grammar + select +
  pagination + totalCount).
- `PATCH /v2/table-containers/{appId}/rows/{rowAppId}`.
- `DELETE /v2/table-containers/{appId}/rows/{rowAppId}`.
- `TableRowDAO` (JDBC against the Postgres table).
- `TableRowQueryService` (filter grammar → parameterised SQL).
- Permission gate (REST inheritance from parent Collection).
- Tests: round-trip, filter coverage, batch-insert idempotency,
  permission denial.
- Toggle still `false`. Smoke tested via integration tests + admin
  override.

### TB1d — permission bootstrap + RLS (1 PR, M)

- `TablePermissionBootstrap`: creates `shepard_c_<collectionSlug>`
  schema + `shepard_r_<collectionAppId>` role + RLS policy on
  `shepard_ts.measurements` + GRANTs on tables in the schema.
- `TablePermissionBridge`: listens for `:HAS_READER/WRITER/MANAGER`
  edge changes and issues matching GRANT/REVOKE.
- Backfill job for existing Collections (no-op if `shepard.tables.enabled
  = false`).
- Tests: permission revoke propagates in <30s; RLS denies cross-tenant
  TS read.

### TB1e — frontend grid + creation wizard (1 PR, L) — **FIRST USER-VISIBLE SLICE**

- `<TableContainerListPane>` mounted on DataObject detail page.
- `<TableContainerPane>` with Data tab only (TanStack Table v8).
- Two-step creation wizard.
- T1e template-driven pre-fill.
- Tests: Vitest unit tests, Playwright at 4K viewport (CLAUDE.md
  `feedback_validate_user_viewport.md`).
- Toggle flips: `shepard.tables.enabled` default `true` in the
  feature-flag PR (separate, after smoke pass).
- aidocs/42 vision update + aidocs/44 row flip to ✓.

### TB1f — SQL surface (1 PR, M)

- `POST /v2/table-containers/{appId}/sql` with `jsqlparser`-based
  validator.
- `TableSqlExecutor` with per-Collection role + cursor + timeout.
- SQL tab in frontend grid.
- `POST /v2/collections/{appId}/sql-credentials` (Keycloak service
  account integration).
- Tests: every banned SQL form rejected with 400 + correct problem JSON;
  permission denial via RLS without REST gate; long-running query
  timeout.

### TB1g — TS-join helper + REST `/join-ts` (1 PR, S)

- `TableTsJoinService` translates the REST helper request to the
  equivalent SQL.
- `POST /v2/table-containers/{appId}/join-ts`.
- Frontend "Join with TS" affordance in the Data tab.

### TB1h — MCP tools (1 PR, S)

- `table_get_schema`, `table_query`, `table_sql`, `table_append_rows`.
- `MCP-COV-TB1` row in aidocs/16 closed.
- Tests: integration against a seeded TB1 table.

### TB1i — Grafana / Superset recipe doc (1 PR, S)

- `docs/admin/runbooks/tb1-grafana-datasource.md`: step-by-step.
- `docs/admin/runbooks/tb1-superset-datasource.md`.
- Both reference the §6.3 service-account flow.

### TB1j — destructive schema mutations + backup snapshots (1 PR, M)

- `POST /v2/admin/table-containers/{appId}/schema/destructive-mutation`.
- `POST /v2/admin/table-containers/{appId}/backup-snapshot` (pg_dump
  per-table; landed in MinIO/Garage).
- Schema migration via shadow-table-swap.
- Admin UI surface.

**Phase ordering rationale.** TB1a → TB1b → TB1c gets the backend
correct in three small reviewable PRs without exposing anything to
users. TB1d is the permission shield; landing it before TB1e prevents
any window where user-visible writes could be permission-incorrect.
TB1e is the smallest user-visible slice that justifies the toggle
flip (basic-mode researcher demand: "I can put my Excel sheet in
Shepard"). TB1f–h are the differentiator layer; TB1i closes the
operator story; TB1j is the destructive-ops escape hatch that's
only safe to ship after months of read-only confidence.

---

## §11 Open questions (for the operator before implementation starts)

These need explicit decisions before TB1a lands.

### §11.1 Per-table Postgres schema vs one shared schema

**Question.** Does each Collection get its own Postgres schema
(`shepard_c_<slug>`), or do all TB1 tables live in one shared
schema (`shepard_tables`)?

**This doc proposes.** Schema-per-Collection. Justification: clean
permission story (GRANT per schema, no per-table grant explosion);
clean SQL editor experience (schema browser shows only "your"
tables); clean delete (DROP SCHEMA CASCADE on Collection delete).
Cost: more schemas, slightly more PG catalog overhead.

**Alternative.** One shared schema with per-table RLS policies.
Pro: fewer schemas. Con: cross-table query explosion of policy
checks; RLS policies aren't free for joins.

**Decision needed:** confirm per-Collection schema, or push toward
shared schema for v1.

### §11.2 Time-bucket joins — FDW vs view vs raw cross-schema?

**Question.** When a TableContainer column references a TS channel,
how does the JOIN actually traverse from
`shepard_c_<col>.table.channel_uuid` to
`shepard_ts.measurements.value`?

**This doc proposes.** Plain cross-schema JOIN — Postgres handles
this natively, both schemas are in the same instance, the FK is
explicit. No FDW, no view layer.

**Alternative A.** Materialised view per-Collection that pre-joins
TS data: faster but stale; cache-invalidation problems.

**Alternative B.** A `shepard_meta.ts_channels_for_collection` view
that filters TS rows per Collection: pre-applies RLS at the view
level.

**Decision needed:** confirm plain cross-schema JOIN; alternatives
are v2 if performance bites.

### §11.3 Row-level security via PG RLS vs service-layer gate?

**Question.** Should RLS be enabled on `shepard_ts.measurements`
(defence in depth — Postgres enforces even for SQL clients), or do
we rely on the JAX-RS filter + SQL validator alone?

**This doc proposes.** Both — defence in depth. The SQL validator
restricts the schema set; RLS enforces row-level visibility within
the schema. Either alone is a single point of failure.

**Cost.** RLS adds ~5-15% query latency on hypertable scans per
TimescaleDB benchmarks.

**Decision needed:** accept the RLS overhead, or rely on validator
alone in v1?

### §11.4 Schema evolution — additive only vs full migration?

**Question.** The doc proposes additive-only PATCH `/schema` with a
separate destructive endpoint. Is that enough, or should destructive
mutations land same-PR as the additive ones for completeness?

**This doc proposes.** Additive-only in TB1b; destructive in TB1j.
Justification: destructive ops require backup, admin role, and audit
guarantees that take time to get right; shipping the read-and-grow
shape first lets users adopt without risk.

**Decision needed:** confirm sequencing, or push for combined PR?

### §11.5 What's the v1 column-type set?

The doc proposes 12 column types (§4). Likely-debated:
- **Should `NUMERIC(p,s)` be in v1**, or punt to v2? It adds
  arithmetic precision (calibration certs, mass measurements) but
  complicates the type-conversion table.
- **Should `RELATION_TABLEROW` be in v1**, or do we stage the
  cross-table-join semantics? It's the foundation for "instruments
  → assignments → persons" joins — losing it from v1 cuts much of
  the inventory use case.
- **Should `JSON` (jsonb) be allowed**? It violates "typed columns"
  but it's the obvious bridge for legacy spreadsheets.

**Decision needed:** confirm v1 type set.

### §11.6 Slug collision policy

**Question.** Two Collections named "LUMEN 2026" — do their schemas
collide? The doc proposes appending `_2`, `_3`, … to the slug. What's
the cleanest UX?

**Alternative.** Use the appId suffix directly:
`shepard_c_019e7243_f995`.

**Decision needed:** slug + counter (human-readable, Grafana-usable)
vs appId (collision-free, cryptic).

### §11.7 Postgres `pgjwt` extension — license + production-readiness?

**Question.** `pgjwt` is the proposed mechanism for JWT-as-password.
It's MIT-licensed but maintenance is sporadic. Alternatives include
PgBouncer-side JWT validation (the JWT becomes a session ticket the
pool resolves to a role) or a small sidecar that translates JWT to
Postgres credentials.

**Decision needed:** pick the JWT-on-Postgres mechanism before TB1f.

### §11.8 Should `RELATION_DATAOBJECT` be enforced by FK?

The doc proposes maintaining `shepard_meta.dataobject_appid_index`
(a UUID → neo4j-id mapping) to allow FK enforcement on DataObject
references. The plugin would maintain this index on every DO write.

**Alternative.** Loose reference — TB1 stores the UUID without FK,
relies on the application to validate.

**Trade-off.** FK enforcement = real referential integrity but
requires maintaining the index table on every DO change (a write
hook in the core). Loose ref = no core change but dangling refs
possible.

**Decision needed:** confirm FK + index table, or loose ref for v1?

---

## §12 Persona-board lens

Per CLAUDE.md "Always: agents argue + consult persona board".

### §12.1 The Reluctant Senior Researcher: "Excel is fine, why TB1?"

> "I've maintained my master spreadsheet for 12 years. It has 600
> rows, every cell has a meaning I remember, and I can scan it in
> 30 seconds. You want me to put it in a database? Why?"

The answer TB1 gives:
1. **You can still scan it in 30 seconds** — the grid is a
   spreadsheet at heart, same column layout, same row anchor. We
   don't ask you to re-learn anything.
2. **The one thing your spreadsheet can't do**: link a row to a TS
   measurement by ID and JOIN on a time window. Today you Excel-export
   the TS, paste it next to your sheet, and `=VLOOKUP`. TB1 does this
   as one SQL query, every time, automatically.
3. **Your spreadsheet has no audit trail.** TB1 records every row
   edit as a typed `:Activity` — when your colleague disputes a
   reading, you can show exactly who changed what, when, and why.
4. **You can still keep your Excel.** Export round-trip is part of
   the spec — CSV/XLSX import on creation, CSV/XLSX export from the
   Data tab. We don't lock you in.
5. **It survives when your laptop dies.** Not a small thing.

The "killer demo" the persona would respond to: import the master
spreadsheet on the left side of the screen; on the right, a
TimescaleDB query of TS-004's vibration channel that auto-correlates
with the inspector-on-shift entry from the spreadsheet, all in one
view.

### §12.2 The Digital Native Researcher: "Can I run pandas against this?"

> "Just give me a JDBC URL. I'll write the query."

The answer TB1 gives:
1. **Yes — directly.** `psycopg2.connect(host="shepard-postgres",
   user=os.getenv("SHEPARD_USER"), password=keycloak_jwt())`.
2. **`pd.read_sql_query("SELECT * FROM shepard_c_lumen.tr_instruments",
   conn)`** returns a DataFrame. No custom adapter.
3. **JOIN with TS data** is a regular SQL JOIN against
   `shepard_ts.measurements`. The 5-tuple is gone — `channel_uuid`
   is one column.
4. **Token rotation** is automatic — Keycloak issues JWTs, Postgres
   validates them, you don't manage credentials.
5. **MCP tools** (`table_sql`) let Claude write the query for you.

The friction TB1 removes: the digital native's current workflow is
"export CSV → Pandas → analyse → can't get back into Shepard." TB1
makes "stay in SQL the whole time" viable, and `table_append_rows`
closes the write-back loop.

### §12.3 IME / AQE (Industrial Manufacturing / Aerospace Quality Engineer): "Audit trail per row?"

> "EN 9100 requires every quality record to be traceable to who
> entered it, when, with what authorisation, and against what
> equipment-calibration state. Can your tables do that?"

The answer TB1 gives:
1. **Every row INSERT, UPDATE, DELETE is a typed `:Activity`** wired
   to `:User` (`WAS_ASSOCIATED_WITH`), to the row's appId
   (`GENERATED` / `USED`), to the source mode (`human` / `ai` /
   `collaborative` from `X-AI-Agent` header), with before/after
   column values for UPDATE.
2. **Schema evolution is also a typed Activity** — adding a column
   to an NCR table is an auditable event, not a silent DDL.
3. **Rows can have a SHACL-validated `RELATION_DATAOBJECT` to a
   calibration certificate DataObject** — so "instrument X was
   calibrated by cert Y at time T" is a real, joinable FK, not a
   spreadsheet cross-reference.
4. **The SQL surface is read-only** — there's no way to mutate row
   data without going through the audit-emitting REST endpoint.
   This is the seam that makes the audit trail forensically
   trustworthy.
5. **`auditHmac` chain** (HmacChainService.stamp() best-effort
   secondary write) gives tamper-evidence on the Activity chain.

The killer demo: TR-004 anomaly → linked NCR row in TB1 → row history
shows initial OPEN, then UPDATE by inspector A at T+12h with
"awaiting concession", then UPDATE by AQE at T+36h to CLOSED with
the rework-DataObject linked. Every step a typed Activity. Pull
audit ledger; show EN 9100 auditor; pass.

### §12.4 Opposing-lens paragraph (per `feedback_agents_argue_and_consult.md`)

The **anti-TB1 lens** — "this is feature creep masquerading as
plugin-first" — argues: shepard's whole value is its graph + payload
model, and adding tabular data inside Postgres dilutes that. A
researcher with table data should either (a) use a
StructuredDataContainer with JSON shape (less typed but already
exists) or (b) use a real database alongside shepard. By shipping
TB1 we admit the graph model isn't enough, and we end up half-Excel,
half-Neo4j, half-Postgres — three half-things, none whole.

The counter: shepard's graph model IS enough for *graph*-shaped data.
Tabular data is structurally distinct — it has columnar query needs,
SQL-tooling demands, and a join-pattern that Cypher doesn't serve.
By owning it natively (rather than punting to "go use Coscine
alongside us"), we keep the FAIR-R1 promise: every piece of a
researcher's data lives in one substrate, with one identity, one
audit trail, and one access control story. The fragmentation risk is
real, but the alternative is worse: every researcher we serve today
*already* maintains an Excel sheet outside shepard. TB1 closes that
gap; not shipping it leaves the gap forever.

The persona-board sign-off requires this counter to land. The
opposition is genuine — TB1 does add scope. The answer is that the
scope was already being paid in the form of the Excel-sheet drift;
TB1 reclaims it.

---

## Closing — what this design commits us to

1. **One canonical SSOT for TB1** (this doc) — `project_table_container.md`,
   `casestudy_table_container.md`, aidocs/40 §140, and the three
   PLUGIN-TABLES-AUDIT rows all collapse to here. Per CLAUDE.md
   `feedback_ssot_per_concept.md`.
2. **Ten phased PRs** (TB1a–j), each independently reviewable, each
   safe under the `shepard.tables.enabled=false` toggle until TB1e.
3. **Reuse first** — no new sidecar, no new substrate, no new auth
   model. The schema-as-template choice in §4 is the central
   architectural commitment that makes TB1 a thin plugin rather than
   "shepard 2.0."
4. **The TS-join differentiator** is the value bet (§5). If the join
   experience is smooth — TanStack grid, REST `/join-ts`, MCP
   `table_sql` — TB1 becomes the answer to every "where do I keep
   my spreadsheet?" friction in the LUMEN/MFFD workflow.
5. **Open questions in §11 need explicit answers** before TB1a
   begins. Eight decisions; this doc proposes a default for each
   but flags it for confirmation.

Next step on landing: operator answers §11; we open TB1a as the
first PR.

---

## §13 Proposed `aidocs/16-dispatcher-backlog.md` rows

Copy-paste-ready rows to append to the dispatcher-backlog table.
These supersede `PLUGIN-TABLES-AUDIT-2026-05-24-003` (the umbrella),
which should be closed and replaced by these ten finer-grained rows.
`PLUGIN-TABLES-AUDIT-2026-05-24-001` (the design-doc row) closes
with the landing of this design.

| ID | Description | Size | Status | Notes |
|---|---|---|---|---|
| TB1a | **TB1 backend skeleton + meta schema.** Maven module `plugins/tables/` (mirrors `plugins/hdf5/`). `TablePluginManifest`, `TablePayloadKind` (ServiceLoader-registered). `TableContainer` Neo4j entity + DAO + IO. Flyway migrations V2.0.0–V2.0.4 (meta schemas, `pgjwt` install, `dataobject_appid_index`, `slug_registry`, `schema_evolution`). `:TableContainerConfig` runtime singleton + admin GET endpoint only. Toggle `shepard.tables.enabled` default `false`. | S | queued | aidocs/platform/111 §10 TB1a. |
| TB1b | **Schema-as-template + DDL translator.** `kind = TABLE_SCHEMA` added to `ShepardTemplate`. `TableDdlService` translates SHACL → CREATE TABLE / additive ALTER TABLE. Column-type ↔ Postgres-type map (12 types per §4). REST: `POST /v2/table-containers`, `GET /{appId}`, `GET /schema`, `PATCH /schema` (additive only). Schema evolution → `:SchemaEvolutionActivity`. No row CRUD yet. | M | queued | aidocs/platform/111 §10 TB1b. Gated on TB1a. |
| TB1c | **TB1 row CRUD (REST).** `POST /rows` batch insert with `onConflict`. `GET /rows` with filter grammar (`field:op:value`), `select`, `sort`, `limit`, `offset`, `totalCount`. `PATCH /rows/{rowAppId}`, `DELETE /rows/{rowAppId}`. `TableRowDAO` (JDBC). `TableRowQueryService` (filter grammar → parameterised SQL). REST permission gate from parent Collection. Per-row `:RowMutationActivity`. | M | queued | aidocs/platform/111 §10 TB1c. Gated on TB1b. |
| TB1d | **TB1 permission bootstrap + RLS.** `TablePermissionBootstrap`: schema-per-Collection + role-per-Collection + RLS on `shepard_ts.measurements`. `TablePermissionBridge` syncs Neo4j `:HAS_READER/WRITER/MANAGER` changes to PG GRANT/REVOKE. Backfill job for existing Collections. Defence in depth before any user-visible surface. | M | queued | aidocs/platform/111 §10 TB1d. Gated on TB1c. |
| TB1e | **TB1 frontend grid + creation wizard (FIRST USER-VISIBLE SLICE).** `<TableContainerListPane>` on DataObject detail page. `<TableContainerPane>` with Data tab (TanStack Table v8 + Vuetify). Two-step creation wizard (template browser → name+bind). T1e template-driven row pre-fill. Vitest + Playwright at 4K viewport. Toggle flips `shepard.tables.enabled` default `true` in a separate follow-up PR after smoke. Triggers `aidocs/42` + `aidocs/44` updates. | L | queued | aidocs/platform/111 §10 TB1e. Gated on TB1d. |
| TB1f | **TB1 SQL surface.** `POST /v2/table-containers/{appId}/sql` with `jsqlparser` SELECT-only validator (rejects 17+ banned forms with 400 + problem JSON). `TableSqlExecutor` opens per-Collection role connection, enforces `statement_timeout` + cursor LIMIT cap. Frontend SQL tab with CodeMirror + Postgres autocomplete. `POST /v2/collections/{appId}/sql-credentials` (Keycloak service account flow). | M | queued | aidocs/platform/111 §10 TB1f. Gated on TB1e. |
| TB1g | **TB1 TS-join helper.** `TableTsJoinService` translates REST join request to SQL JOIN against `shepard_ts.measurements`. `POST /v2/table-containers/{appId}/join-ts`. Frontend "Join with TS" affordance in Data tab. The casual-user equivalent of writing the SQL by hand. | S | queued | aidocs/platform/111 §10 TB1g. Gated on TB1f. |
| TB1h | **TB1 MCP tools.** `table_get_schema`, `table_query`, `table_sql`, `table_append_rows` under `shepard-plugin-mcp`. Closes `MCP-COV-TB1` sub-row. Integration tests against seeded TB1 table demonstrate Claude reaching "TR-004 NCRs joined with anomaly-window TS" in three MCP calls. | S | queued | aidocs/platform/111 §10 TB1h. Gated on TB1f. |
| TB1i | **TB1 Grafana + Superset recipe docs.** `docs/admin/runbooks/tb1-grafana-datasource.md` + `docs/admin/runbooks/tb1-superset-datasource.md`. Step-by-step including the §6.3 service-account flow + `$__timeFilter` patterns + cross-substrate-join example with `shepard_ts.measurements`. | S | queued | aidocs/platform/111 §10 TB1i. Gated on TB1f. |
| TB1j | **TB1 destructive schema mutations + backup snapshots.** `POST /v2/admin/table-containers/{appId}/schema/destructive-mutation` (DROP COLUMN, RENAME, DROP CONSTRAINT) with `ackDataLoss` + admin role + backup-confirmation token. `POST /v2/admin/table-containers/{appId}/backup-snapshot` (pg_dump per-table → MinIO/Garage). Shadow-table-swap implementation. Admin UI surface. Last phase — only safe after months of read-only confidence. | M | queued | aidocs/platform/111 §10 TB1j. Gated on TB1e + ops confidence. |

**Rows superseded by this design (close on TB1a land):**

- `PLUGIN-TABLES-AUDIT-2026-05-24-001` — design doc shipped (this file).
- `PLUGIN-TABLES-AUDIT-2026-05-24-003` — umbrella replaced by TB1a–j.

**Row gating from outside TB1:**

- `POSTGRES-MULTITENANT-SCHEMA-DECISION` and `PG-COLLAPSE-001` — TB1
  works against today's single-PG-instance shape but the schema layout
  in §5.1 assumes the collapse has happened. If PG-COLLAPSE-001 hasn't
  shipped by TB1d, TB1d adds a fallback path using only the TS-Postgres
  instance and defers PostGIS-table joins to TB1g+1.
- `PLUGIN-TABLES-AUDIT-2026-05-24-004` (cross-substrate decision) —
  subsumed by `POSTGRES-MULTITENANT-SCHEMA-DECISION`; close when that
  one decides.
