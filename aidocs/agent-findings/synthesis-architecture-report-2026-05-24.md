---
stage: feature-defined
last-stage-change: 2026-05-24
audience: [operator, contributor, instance-admin]
inputs:
  - ts-design-audit-2026-05-24.md
  - postgres-pgbouncer-substrate-audit-2026-05-24.md
  - mongodb-substrate-audit-2026-05-24.md
  - file-storage-routing-audit-2026-05-24.md
  - garage-and-docker-stack-audit-2026-05-24.md
  - neo4j-n10s-design-audit-2026-05-24.md
  - persona-reluctant-senior-2026-05-24.md
  - persona-manufacturing-quality-2026-05-24.md
  - persona-digital-native-2026-05-24.md
  - plugin-design-audit-2026-05-24.md
---

# Synthesis architecture report — 2026-05-24

Ten audits landed on the same day against the live nuclide deploy
(six substrate, three personae, one plugin-design). This document is
the cross-cutting consolidation: not a re-run of any finding, but the
map of where they meet, where they conflict, and what the dependency
chain says we ship next. The depth lives in the source audits — every
verdict here is a one-sentence read followed by the receipts.

---

## §1 — Executive snapshot

**Substrate-by-substrate health (one sentence each):**

- **TimescaleDB** — healthier than the prior hypothesis suggested
  (23× compression ratio, sub-millisecond hot-path reads), but
  carrying two latent CRITICALs at the write path (string-INSERT
  per batch, 4-column polymorphism) and one universal observability
  blind spot (no `pg_stat_statements`).
- **Postgres + PgBouncer** — non-TS workload is tiny and well-shaped,
  but the runtime is wrong in five places at once: zero backups, a
  healthcheck loop emitting 17 K errors/day, host-RAM mis-sizing on a
  shared box, two unbounded timeouts, and a dead `tweak-db-settings.sql`
  that contradicts the live config.
- **MongoDB** — 99 % of substrate weight is GridFS chunks and 60 % of
  those chunks are one 532 MB Confluence ZIP; schema discipline gaps
  (zero validators, zero TTL, string-typed `FileMongoId` joining
  ObjectId-keyed `fs.files`) plus an N+1 in SD search are the real
  ceiling.
- **File-storage routing** — Garage works, but two of three write
  paths bypass the `FileStorageRegistry` entirely, so 11 788 of
  11 902 `:ShepardFile` rows still land in GridFS despite
  `SHEPARD_STORAGE_PROVIDER=s3` having been set since today.
- **Garage + Docker stack** — Garage is correctly under-provisioned
  (1 GB cap), over-permissive on two surfaces (admin token absent,
  rpc-secret committed), and ten of thirteen containers carry no
  `mem_limit` and no healthcheck; five images still float on `:latest`.
- **Neo4j + n10s** — 87 constraints + 115 indexes + 13 ontologies
  loaded look right; underneath, 284 535 `:Activity` nodes carry zero
  edges (the f(ai)²r promise unbacked), 99.4 % of `:ShepardFile.providerId`
  is NULL, and 100 % of `:Timeseries.appId` is NULL despite a
  uniqueness constraint already in place.

**Persona snapshot:** the FAIR-band shipments of the last fortnight
(LIC1, completeness card, Cite-this, ORCID, PROV resolver fix) moved
the Reluctant Senior from "I would not adopt" to "the gap shrank";
moved the IME/AQE needle by **+1.75 EN 9100 clauses** (almost entirely
from the PROV-resolver fix); and dropped the Digital Native's score
from 8/10 → **7.5/10** because the public hostname does not expose the
API and the documented `containers.timeseries[]` happy-path returns
empty on TR-004.

**The three architecturally-decisive findings (biggest leverage on
the next year's roadmap):**

1. **`:Activity` is provenance write-only** — 284 535 nodes, zero
   edges, the entire f(ai)²r promise resting on properties the read
   path can't traverse. [NEO-AUDIT-001]
2. **The FileStorageRegistry is selectively honoured** — registry
   exists, the SPI works, two of three write paths bypass it.
   Compose flips don't propagate. [FS1-ROUTING-FIX-FORWARD +
   NEO-AUDIT-002]
3. **Substrate is ahead of integration in five places** — Activity
   edges (substrate accepts, writer doesn't emit), TS.appId
   (constraint + index provisioned, data empty), file-routing
   registry (exists, bypassed), status vocab (server free-form, UI
   hardcoded), Completeness score (client-pure-fn, no server
   endpoint). The cheap wins live in **wiring, not schema**.

**The one thing that, if shipped tomorrow, removes the most
cross-substrate pain:** **wire the three PROV-O edges on the Activity
write path (NEO-AUDIT-001) + back-fill the existing 284 K rows.** It
closes a vision claim already made, retires NEO-005's EAV symptoms by
absorbing import provenance off DataObjects, makes the IME/AQE §7.5.3
audit trail traversable, makes the Digital Native's "what happened to
TR-004" reachable from MCP without numeric ID guessing, and pairs
naturally with the already-shipped PROV-RESOLVER-PATHWALK. One
plugin-architecture-style change with three audits' worth of payoff.

---

## §2 — Cross-cutting antipattern matrix

Twelve patterns surface in ≥ 2 audits each. CRITICAL severity is
reserved for patterns that meet the brief's "≥ 2 substrate audits"
threshold and would block a production-grade deploy review.

| # | Pattern | Substrates / audits | Load-bearing backlog rows | Retire-cost |
|---|---|---|---|---|
| AP-X1 | **String-typed identity where the substrate offers stronger typing.** Status free-text on DO; Activity.actionKind free-text; FileMongoId stored as string but `fs.files._id` is ObjectId; v1 numeric IDs still in PROV-RESOLVER-PATHWALK lookups. **CRITICAL** | Mongo (MONGO-007), Neo4j (NEO-015 actionKind), Postgres metadata (PG `timeseries` 5-tuple), IME persona §"status vocabulary" | NEO-AUDIT-015, MONGO-AUDIT-007, IME-Primitive-1 | M — one config singleton + per-substrate validation pass |
| AP-X2 | **Write path bypasses its own registry / SPI seam.** FileStorageRegistry skipped by SingletonFileReferenceService + FileBundleReferenceRest; Activity write path skips the documented PROV-O edges; status vocab hard-coded in one Vue file instead of read from server registry. **CRITICAL** | File-routing (FS1-FIX-FORWARD), Neo4j (NEO-001), IME persona §1 | FS1-ROUTING-FIX-FORWARD, FS1-SINGLETON-PRESIGNED, NEO-AUDIT-001, IME-Primitive-1 | M — three site-specific patches, no schema change |
| AP-X3 | **No backup configured for any persistent substrate.** No `pg_dump`, no Garage volume snapshot, no Neo4j dump cron, no Mongo dump cron. **CRITICAL** | Postgres (PG-001), Garage (GARAGE-003), Stack (STACK-009 mixed-strategy note), Neo4j (community edition, operator-side answer per audit §"Stack-level"), Mongo (no audit row but absence confirmed) | PG-AUDIT-2026-05-24-001, GARAGE-AUDIT-2026-05-24-003, ADMIN-RUNBOOKS-LIBRARY | L — one cron + Garage-target bucket + cross-substrate runbook |
| AP-X4 | **Healthchecks declared without consumers, or consumers without healthchecks.** Pgbouncer healthcheck spams 17 K errs/day (no-DB-name); 8 of 10 services lack any healthcheck so `depends_on: service_healthy` is process-up only; OPS-MIGRATION-HEALTHCHECK shipped but storage-backend-readiness still missing. **CRITICAL** | Postgres (PG-002), Stack (STACK-002 + STACK-010 race), file-routing (FS-STORAGE-READINESS-HEALTHCHECK), Garage (GARAGE-004 admin endpoint dark) | PG-AUDIT-2026-05-24-002, STACK-AUDIT-2026-05-24-002, FS-STORAGE-READINESS-HEALTHCHECK | S — one-line compose fixes + four `CMD` definitions |
| AP-X5 | **Image-tag drift / `:latest` pinning.** keycloak, pgbouncer, mongo-express, dozzle, alloy, arcane all float; locally-built backend + frontend carry no git-sha label. **CRITICAL** | Stack (STACK-003 + STACK-006), Postgres (PG-006) | STACK-AUDIT-2026-05-24-003, STACK-AUDIT-2026-05-24-006, PG-AUDIT-2026-05-24-006 | S — digest-pin sweep + Dockerfile `--build-arg GIT_SHA` |
| AP-X6 | **Observability blind spots.** No `pg_stat_statements`, no `log_min_duration_statement`, no Garage `admin_token`, no in-Shepard providerId-histogram, no MongoDB pool-sizing telemetry. **MAJOR** | TS (AP-5), Postgres (PG-003, PG-012), Garage (GARAGE-004), file-routing (OBS-MFFD1-PROVIDERID), Mongo (MONGO-009) | TS-AUDIT-2026-05-24-005, PG-AUDIT-2026-05-24-003, GARAGE-AUDIT-2026-05-24-004, OBS-MFFD1-PROVIDERID | XS — one config change per substrate |
| AP-X7 | **One-collection-per-X / per-row data-shape proliferation.** Per-FileContainer + per-StructuredDataContainer Mongo collection; per-chunk FK on TS hypertable; per-Activity property scan instead of edge. **MAJOR** | Mongo (MONGO-002), TS (AP-4 FK-on-hypertable), Neo4j (NEO-001 + NEO-005) | MONGO-AUDIT-2026-05-24-002, TS-AUDIT-2026-05-24-004, NEO-AUDIT-2026-05-24-001, SD1 | L — per-substrate redesigns; sequence after their dependencies |
| AP-X8 | **Tombstones without GC.** 4 197 `:StructuredDataContainer{deleted:TRUE}` in Neo4j with no Mongo collection; 50 % of `:DataObject` and 36 % of `:BasicReference` soft-deleted; no TS retention policy. **MAJOR** | Neo4j (NEO-016), Mongo (MONGO-002 cross-substrate), TS (AP-7) | SM1a, NEO-AUDIT-2026-05-24-016, TS-AUDIT-2026-05-24-007 | M — sweep service + retention policy decision |
| AP-X9 | **Sibling-substrate split where a schema split would suffice.** Postgres for TS + a separate `postgis` Postgres + a planned-but-not-shipped Tables Postgres; HSDS on POSIX instead of Garage. **MAJOR** | Plugin-design (Spatial-004, Tables-004), HDF5 (PLUGIN-HDF5-003) | PLUGIN-SPATIAL-AUDIT-2026-05-24-004, PLUGIN-TABLES-AUDIT-2026-05-24-004, POSTGRES-MULTITENANT-SCHEMA-DECISION | L — operator-side compose + JDBC URL re-point; bench-marked at zero app-code change |
| AP-X10 | **Sidecar declaration debt.** PM1f shipped on `file-s3`; spatial + hdf5 keep their sidecars in central compose; future Tables/Matrix/Importer/Wiki-writer plugins inherit the same shape. **MAJOR** | Plugin-design (PLUGIN-SPATIAL-001 + PLUGIN-HDF5-001 + cross-cutting #1), Stack (compose duplication note) | PM1f-MIGRATION-SPATIAL-HDF5-2026-05-24, STACK-AUDIT-2026-05-24-005 (dead network) | S — two manifest-method overrides + compose hygiene |
| AP-X11 | **EAV / property-scan replacements for first-class edges.** 90+ `attributes||*` keys on `:DataObject`; `Activity.targetAppId` scan as edge proxy; `:DataObject.license` (3 rows) coexisting with typed `license` (4 rows, LIC1). **MAJOR** | Neo4j (NEO-005 + NEO-017), file-routing (cross-substrate route through `attributes`), IME persona §"as-built deviation surfaces" | NEO-AUDIT-2026-05-24-005, NEO-AUDIT-2026-05-24-001 | M — SHACL-direction decision + import-prov edge promotion |
| AP-X12 | **Pattern documented but unwired in code.** PROV-O edges TODO since first ship; `_meta` envelope on SD without backend wrap fallback; `application.properties` no DB name + silent default; `getauth` healthcheck silenced rather than fixed at source. **MAJOR** | Neo4j (NEO-001), Mongo (MONGO-004 + MONGO-011), Postgres (PG-002) | NEO-AUDIT-2026-05-24-001, MFFD-SD-BACKEND-WRAP-FALLBACK | M — documentation-vs-code reconciliation discipline |

Twelve patterns; five CRITICAL (each ≥ 2 substrate audits), seven
MAJOR. Patterns AP-X1 + AP-X2 dominate the cleanup roadmap because
they explain why so many cheap fixes have so much leverage: most
defects are in **integration**, not schema. AP-X9 is the only
genuinely structural one in the list — every other row retires with
a sprint or less of focused work.

---

## §3 — Architectural tensions

Five real tensions the audits expose. For each: both sides honestly,
the decisive constraint, the recommended cut.

### T1 — SHACL-as-source-of-truth vs Neo4j-as-typed-store

**Side A (SHACL-as-SoT, per memory + `feedback_shacl_single_source_of_truth.md`):** domain shapes live in SHACL turtle, every substrate is a *projection* of those shapes; runtime validation is SHACL conformance against the Neo4j+RDF view. Side closes AP-X11 by construction (you can't write a stray attribute on a DataObject if SHACL rejects it) and closes AP-X1 if shapes carry value-vocab constraints. **Side B (Neo4j-as-typed-store):** Neo4j-OGM is already the typed store; @Properties already enforces what enters the graph; SHACL becomes redundant overhead. Live evidence supports Side B for *some* shapes (the `idx_Subscription_requestMethod` is well-typed and the FAIR fields shipped on `AbstractDataObject` are simple String columns the OGM handles fine) but Side A for *others* (the EAV blow-up on `:DataObject.attributes||*` — 90+ keys — is exactly what SHACL would catch).

**Decisive constraint:** the EAV evidence is overwhelming. NEO-AUDIT-005 catalogues 90+ distinct attribute keys; NEO-AUDIT-017 quantifies the page-cache cost; the IME persona §"as-designed vs as-built" needs typed deviation surfaces that EAV cannot serve. Side B has no answer for "how does adding `material_batch=LH2-2024-06-01` get gated when the field doesn't exist in the OGM yet?"

**Cut:** **Side A wins for new shapes; Side B keeps the legacy `attributes||*` field as a graveyard.** Promote import-provenance attributes off `:DataObject` onto `:Activity` edges (NEO-AUDIT-005 + NEO-AUDIT-001). Domain shapes flow through SHACL. The legacy bag stays as an open-world escape hatch with a documented retire path. Backlog row to file: `SHACL-AS-SOT-DECISION-RECORD` referencing `feedback_shacl_single_source_of_truth.md`.

### T2 — One Postgres + N schemas vs N Postgres instances

**Side A (one Postgres, multiple schemas):** PostgREST adoption (Tables plugin §F), real SQL joins across `shepard_ts`/`shepard_tables`/`shepard_spatial`, PgBouncer per-schema pools, one backup target. **Side B (per-plugin Postgres):** blast-radius isolation (a Tables-runaway can't OOM the TS pagecache), independent version + restart cadence, separate `mem_limit` per workload.

**Decisive constraint:** PG-AUDIT-005 quantifies the cost of side B *today* — `shared_buffers=15.5 GB` on a 32 GB host shared with Neo4j+Mongo+JVM is already over-provisioned. Adding a Tables instance and keeping the Spatial instance makes it strictly worse. The IME persona's killer-demo claim ("native Grafana PG joining TS with Tables") requires Side A.

**Cut:** **Side A — collapse to one Postgres with three schemas, PgBouncer pool-per-schema.** Land alongside aidocs/82 §2.2 TimescaleDB hypertable conversion. Backlog rows: PLUGIN-SPATIAL-AUDIT-004 + PLUGIN-TABLES-AUDIT-004 + POSTGRES-MULTITENANT-SCHEMA-DECISION. **Counter-evidence to watch:** if the PostGIS workload regularly takes > 30 s per query, the side B isolation argument re-opens; mitigate with `statement_timeout` set at the role level per PG-AUDIT-004.

### T3 — v2-API-only vs hybrid v1+v2 surface

**Side A (v2-API-only, the long-term direction per `aidocs/25 L2e`):** simpler routing, no dual-pagination shape, kills the numeric-vs-appId confusion the Digital Native flagged. **Side B (keep v1 alive per the upstream-byte-compat rule in CLAUDE.md):** zero-friction operator upgrade from upstream 5.2.0; the V1-numeric-ID lookup (PROV-V1-NUMERIC-LOOKUP, shipped) makes v1 callable indefinitely.

**Decisive constraint:** the upstream compat rule is non-negotiable per CLAUDE.md `API-version policy`. **Side B wins by policy** — but per `project_v1_sunset_strategy.md` the sunset is per-operator, not global. The conflict is not "which surface lives", it's **"why is the Digital Native blocked from reaching v2 from the public hostname?"** That's not the API-version tension; that's an authentication-routing failure (Digital Native §0).

**Cut:** Side B stays. **Fix the public-hostname routing immediately** so the v2 surface is reachable via `Authorization: Bearer` / `X-API-KEY` without NextAuth interception (Digital Native §9.1, one-line Caddy rule). Backlog row: `CADDY-API-PASSTHROUGH-2026-05-24`. The "v2-API-only" tension goes away once both surfaces are equally callable.

### T4 — FK on hypertable vs ingest throughput

**Side A (keep FK):** application-level integrity catches "insert into deleted series" before it lands. **Side B (drop FK at scale):** every INSERT triggers a `timeseries.id` lookup; at MFFD-scale this is measurable (TS-AUDIT-AP-4 documents 600 K+ pkey scans for a 867-row metadata table — 85.9 M total scans).

**Decisive constraint:** the per-row cost is real but the substrate is nowhere near where it matters (today's peak is 87 inserts/s). The TS-AUDIT recommends a runtime flag, not removal.

**Cut:** **Keep FK in dev, ship a runtime flag (`shepard.timeseries.fk.enabled`, default true)** so operators can drop FK at scale without code change. Backlog row: TS-AUDIT-2026-05-24-004. Pair with SM1a (orphan retention) so the integrity story shifts from FK-enforced to sweep-enforced cleanly.

### T5 — In-tree frontend vs plugin-frontend SPI

**Side A (in-tree frontend, today's model):** every plugin adds Vue pages directly to `frontend/pages/...`; one build, one CI pipeline. **Side B (plugin-frontend SPI):** plugins ship their own Vue bundle; shepard frontend dynamic-imports per active-plugin manifest.

**Decisive constraint:** plugin-design audit confirms **none of the three plugins owns its frontend** (spatial = page stub, HDF5 = nothing, Tables = nothing). Cross-cutting #5 in that audit calls Side B "out of scope today" but flags it as the structural answer when the third or fourth plugin frontend ships.

**Cut:** **Side A for the next two plugins; design Side B once a fourth plugin (likely Matrix-notifications or Wiki-writer) needs UI surface.** Backlog row already filed: SPI-FRONTEND-CONTRIBUTION-2026-05-24. The tension is real but the trigger threshold isn't reached.

---

## §4 — Reuse-vs-build map

Per CLAUDE.md `feedback_reuse_before_reimplement.md`, every queued
design here gets a one-line reuse-vs-build verdict with the load-bearing
backlog row ID.

| Design / decision | Reuse candidate | Build candidate | Verdict | Backlog |
|---|---|---|---|---|
| Tables plugin CRUD surface | PostgREST + Postgres RLS | In-tree JAX-RS endpoints | **Reuse PostgREST** — collapses two-thirds of plugin into substrate config; auth via gateway proxy + RLS policies. Justification: BSD-2 licence, mature, zero per-record code maintained in shepard. | PLUGIN-TABLES-AUDIT-002 |
| Relationship-type rename (`ENTRY_OF` → `entry_of`, etc.) | APOC `apoc.refactor.setType` | Hand-rolled Cypher `MATCH … CREATE … SET … DELETE` | **Reuse APOC** — but APOC is absent (NEO-013); install first, then ship the rename. | NEO-AUDIT-013 + NEO-AUDIT-006 |
| Slow-query observability | `pg_stat_statements` + `log_min_duration_statement` | Custom Quarkus instrumentation | **Reuse PG built-ins** — already in the recommended-load list; one-line config. | TS-AUDIT-005, PG-AUDIT-003, PG-AUDIT-012 |
| Pool-tuning across N plugins on shared PG | PgBouncer per-schema pool (one bouncer, multiple `[databases]` entries) | One pgbouncer per plugin | **Reuse one PgBouncer** — additive `[databases]` block per schema; sizing already in place. | PG-AUDIT-005 + Tables design |
| HDF5 storage backend | Garage via `HSDS_AWS_S3_GATEWAY` env-flip | Custom Java HDF5 reader (JHDF) | **Reuse HSDS-on-Garage** — zero application code change; gated on ADR-0024 GA. | PLUGIN-HDF5-AUDIT-003 |
| Spatial vector tile rendering | MapLibre GL JS + in-process `ST_AsMVT` | TiTiler / pg_tileserv sidecar | **Reuse MapLibre client + build the MVT endpoint** (~20 LoC) — avoids another sidecar; sidecar adds no value at our scale. | PLUGIN-SPATIAL-AUDIT-003 |
| Browser HDF5 slice preview | h5wasm + Plotly | Round-trip every preview to HSDS REST | **Reuse h5wasm** — 3 MB WASM, keeps frontend independent of HSDS browse-tree fidelity. | PLUGIN-HDF5-AUDIT-002 |
| TS continuous aggregates | TimescaleDB native CA with `materialized_only = false` | App-layer caching in `TimeseriesContainerChartViewService` | **Reuse CA** — substrate-resident, incrementally maintained, microsecond reads on the bucketed path. | TS-AUDIT-006 |
| Tables plugin UI grid | TanStack Table (MIT) | Hand-rolled Vuetify v-data-table extension | **Reuse TanStack** — headless library, pairs cleanly with Vuetify; already a likely shape since frontend uses Composition API. | PLUGIN-TABLES-AUDIT-003 |
| Sidecar declaration for spatial + hdf5 | The PM1f pattern already shipped on `file-s3` | New shape | **Reuse PM1f** — `FileS3PluginManifest.sidecars()` is the reference impl; copy-pattern is the right cost. | PM1f-MIGRATION-SPATIAL-HDF5-2026-05-24 |

**Net read:** nine of ten queued designs reuse an existing component
or substrate feature; one builds (the 20-LoC MVT endpoint). The
reuse-first discipline is paying — the open work this quarter is
largely **wiring**, not new code.

---

## §5 — Cleanup-cost map (operator-facing)

Sorted by **operator-pain-removed ÷ effort-spent**. Dependencies
honoured: prerequisite rows always come first.

| Rank | Row | Effort | Operator pain removed | Depends on | Audit |
|---|---|---|---|---|---|
| 1 | **PG-AUDIT-2026-05-24-002** — fix PgBouncer healthcheck | XS | 17 280 spurious error logs/day → 0; usable log signal | — | Postgres+PgBouncer |
| 2 | **TS-AUDIT-2026-05-24-005** + **PG-AUDIT-2026-05-24-003** — enable `pg_stat_statements` (single restart) | XS | Unblocks every future PG perf audit; gives MFFD-import slow-query visibility | — | TS + Postgres |
| 3 | **STACK-AUDIT-2026-05-24-003** + **PG-AUDIT-006** — digest-pin all `:latest` tags | S | Eliminates unannounced upgrade ambush class; reproducible deploys | — | Stack + Postgres |
| 4 | **NEO-AUDIT-2026-05-24-013** — add `apoc` to `NEO4J_PLUGINS` | XS | Unblocks rel-type rename, batched migrations, JSON imports | — | Neo4j |
| 5 | **NEO-AUDIT-2026-05-24-002** — backfill `:ShepardFile.providerId='gridfs'` on the 11 834 NULL rows | XS | Cleans the dispatch contract for FS1; lets reader-side NULL-coalesce retire | — | Neo4j (closes file-routing #2 from one side) |
| 6 | **CADDY-API-PASSTHROUGH-2026-05-24** — let `Authorization`/`X-API-KEY` through NextAuth on public host | S | Digital Native §0 — API discoverable on `shepard.nuclide.systems` | — | Digital Native persona |
| 7 | **GARAGE-AUDIT-2026-05-24-001** — raise Garage layout capacity to 100 GB | XS | Removes silent-write-block before next MFFD ingest scale | — | Garage |
| 8 | **STACK-AUDIT-2026-05-24-001** — `mem_limit` on every shepard service | XS | One-line per service; closes the 10-service OOM-risk surface | — | Stack |
| 9 | **FS1-ROUTING-FIX-FORWARD** + **FS1-SINGLETON-PRESIGNED** (pair, not solo) | M | Closes the singleton + bundle write-path bypass; future MFFD ingest lands in Garage | Slice depends on the presigned endpoint shipping concurrently — without it, v15 importer hits 503 on s3 | File-routing |
| 10 | **PG-AUDIT-2026-05-24-001** — nightly `pg_dump` → Garage bucket + WAL archive runbook | S | Closes the CRITICAL data-loss gap on Keycloak realm + permission audit + TS metadata | Garage raised (#7) | Postgres + Garage |

**The three rows that should land in the next coherent release per
`aidocs/strategy/85` pre-flight:**

- **#1 + #2 + #4** as one bundled PR ("substrate-observability + APOC + healthcheck-quiet"). Roughly two days of one engineer; touches three substrate audits; ratchets the next round of audits onto real measurement.
- **#5 + #6** as one PR ("file-routing pre-work + API host"). Backfill, plus the Caddy rule; the FS1 fix-forward becomes safe to ship once both are in place.
- **#10** standalone because it crosses an operator-runbook boundary; deserves its own commit + documentation.

Items #7 and #8 are operator-side flips (no code) and should be
applied in the same deploy window as #1–#4.

---

## §6 — Substrate roadmap (3-pass)

Sequencing is dependency-aware. Each row lists the load-bearing audit
source and which persona / external audience would notice.

### Pass 1 — within 1 month (cheap, high-leverage)

| Row | Source audit | Dependency | Persona that notices |
|---|---|---|---|
| NEO-AUDIT-002 — backfill `providerId='gridfs'` | Neo4j | — | none directly; unblocks #FS-cluster |
| NEO-AUDIT-013 — add APOC plugin | Neo4j | — | contributor (re-enables every rec migration) |
| TS-AUDIT-005 + PG-AUDIT-003 — `pg_stat_statements` | TS + Postgres | — | next perf-audit run |
| PG-AUDIT-002 — fix pgbouncer healthcheck | Postgres+PgBouncer | — | operator (log noise → 0) |
| STACK-AUDIT-001 — mem_limit on all services | Stack | — | operator (OOM risk) |
| GARAGE-AUDIT-001 — raise capacity to 100 GB | Garage | — | operator + next MFFD ingest |
| CADDY-API-PASSTHROUGH-2026-05-24 (newly proposed §3-T3) | Digital Native | — | Digital Native (§0 host blocker) |
| FS-STORAGE-READINESS-HEALTHCHECK | File-routing | — | operator (drift visible) |
| TS-AUDIT-003 — `CHECK (num_nonnulls(...) = 1)` on data_points | TS | — | future-multi-type writer |
| MONGO-AUDIT-004 — backend array-wrap fallback for SD payload | Mongo | — | external client (not just importer) |

### Pass 2 — within 1 quarter (load-bearing shape changes)

| Row | Source audit | Dependency | Persona that notices |
|---|---|---|---|
| **NEO-AUDIT-001** — wire PROV-O Activity edges + back-fill 284 K rows | Neo4j (+ touches IME + Digital Native) | PROV-RESOLVER-PATHWALK (shipped) | IME (§7.5.3), Digital Native (MCP), Reluctant Senior (provenance tab) |
| **FS1-ROUTING-FIX-FORWARD + FS1-SINGLETON-PRESIGNED + FS1-ROUTING-MIGRATE-BACKFILL** | File-routing | Pair must ship together; backfill after fix-forward + presigned green | operator (storage choice honoured), Digital Native (singleton uploads via presigned) |
| **TS-AUDIT-002** — route live writes through COPY for batches > 1 000 | TS | — | next MFFD ingest |
| **TS-AUDIT-006** — continuous aggregates for chart-view buckets | TS | TS-AUDIT-005 first (so we can measure improvement) | Reluctant Senior (chart latency at MFFD scale) |
| **PM1f-MIGRATION-SPATIAL-HDF5** — sidecar declarations on both shipped plugins | Plugin-design | — | contributor (plugin manifest shape consistent) |
| **POSTGRES-MULTITENANT-SCHEMA-DECISION** — collapse to one PG + three schemas | Plugin-design + Postgres | Design doc first (no code) | contributor + IME (cross-substrate SQL JOIN works) |
| **NEO-AUDIT-005** — promote import-provenance off `:DataObject.attributes||*` onto `:Activity` edges | Neo4j | NEO-AUDIT-001 shipped | Reluctant Senior (lineage panel populates) |
| **SM1a** — orphan retention sweep (Neo4j tombstones + Mongo collection GC + TS old-chunk consideration) | Neo4j + Mongo + TS | NEO-AUDIT-002 finished | operator (substrate weight bounded) |
| **PG-AUDIT-001** + Garage backup pair — nightly backup target | Postgres + Garage | GARAGE-AUDIT-001 (capacity) | operator (data-loss gap closed) |

### Pass 3 — within 6 months (architectural pivots)

| Row | Source audit | Dependency | Persona that notices |
|---|---|---|---|
| **SHACL-AS-SOT-DECISION-RECORD + first shape rollout** | Neo4j (§3-T1) | T1 cut accepted in writing | contributor + RDM steward |
| **TS-ID migration (`shepardId` as canonical, deprecate 5-tuple)** per aidocs/87 | TS + Neo4j (NEO-003) | TS-AUDIT-002 + NEO-AUDIT-013 (APOC) | Digital Native (5-line Python test passes) |
| **Plugin-frontend SPI** (`SPI-FRONTEND-CONTRIBUTION-2026-05-24`) | Plugin-design cross-cutting #5 | 4th plugin needs UI (likely Matrix or Importer) | contributor + Reluctant Senior (importer UI surfaces) |
| **`shepard-plugin-quality` (NCR / CAR / Calibration primitives)** | IME persona §"Quality gate + NCR routing plan" | NEO-AUDIT-001 (typed edges) + edge-property `transitionKind` | IME (§8.7 + §10.2 + §7.1.5 close) |
| **Status vocab registry singleton + UI v-select reads from server** | IME persona Primitive 1 | — | IME + Reluctant Senior |
| **PostgREST adoption decision for Tables** | Plugin-design Tables §F | Tables SSOT design doc lands first | contributor (zero in-tree CRUD code) |

The shape of the year: **Pass 1 makes the substrate measurable, Pass 2
makes the writes honest, Pass 3 makes the surface domain-extensible.**

---

## §7 — What the audits DIDN'T find

Honest gap-fleet review. Five blind spots, five next-dispatch
candidates.

1. **No Keycloak / OIDC substrate audit.** Keycloak runs on `:latest`, port 8082 exposed on `0.0.0.0`, master-realm admin reachable. The Digital Native §0 finding (NextAuth interception of API tokens) is the symptom; the cause is an unaudited reverse-proxy + auth-broker boundary. **Next dispatch: Auth substrate auditor** — keycloak.conf, realm export, JWT token shape, OIDC client config inventory, MCP PKCE flow, API-key rotation policy.
2. **No Caddy / Zoraxy reverse-proxy audit.** The public hostname's NextAuth interception was discovered by accident; the audit fleet has no read on what other routing decisions are accreting outside compose. **Next dispatch: Edge-routing auditor** — Caddy + Zoraxy config sweep, virtual-host map, ACL inventory, header pass-through rules.
3. **No FAIR data steward re-walk after LIC1 + completeness widget shipped.** The Cite-this card + Metadata Completeness widget are big-deal FAIR shipments that no FAIR-perspective re-audit has scored. The Digital Native + Reluctant Senior personae lap the surface but don't score it against FAIR R1/A1/I1/F1 axes. **Next dispatch: FAIR data steward persona** with the new LIC1 + completeness + ORCID + Cite-this surface in scope, rescore against `aidocs/agent-findings/research-data-manager.md` baseline.
4. **No backup-and-recovery drill across substrates.** PG-AUDIT-001 + GARAGE-AUDIT-003 + the Neo4j Community-edition note all flag backup as missing; no audit *exercises* a recovery. **Next dispatch: Disaster-recovery drill** — script the pre-mutation snapshot pair (per `feedback_mutate_after_snapshot.md`), simulate a `docker volume rm`, time the restore for each substrate, document gaps.
5. **No PostGIS substrate audit (only the `postgis` *container* via plugin-design).** PostGIS sits on a different Postgres instance with the same credentials (PG-AUDIT-011) and is unaudited at the schema / spatial-index / health-routing layer. The plugin-design audit names it as a candidate for collapse (Pass 2 row) but no one has read its tables. **Next dispatch: PostGIS / Spatial-substrate auditor** — schema scan, GIST/BRIN index audit, HASH-partition validation, BBox query plan profiles.

Three smaller blind spots worth a follow-up sweep but not a full
dispatch:

- **No upstream-compat regression test on the file-routing change** (FS1-ROUTING-FIX-FORWARD risks breaking a v5 client that POSTs to `/shepard/api/.../payload` against an s3-active deploy). Add to the FS1 PR's test plan.
- **MongoDB connection-pool unmeasured** (MONGO-AUDIT-009 flags the gap; no audit instruments the actual pool occupancy). One Prometheus query, low-effort follow-up.
- **No JVM heap-pressure audit on the backend container** despite running with `-Xms2G -Xmx2G` on a 32 GB host alongside Neo4j+Mongo+TS heaps. Pair with STACK-AUDIT-001.

---

## §8 — Vision currency

`aidocs/42-vision.md` was last edited 2026-05-23 and is broadly
honest, but the audit harvest exposes three claims that drifted in
the last 48 h. Per CLAUDE.md "Always: keep aidocs/42-vision.md
current", these edits belong with the next shipment:

1. **§"Data forging" + the f(ai)²r paragraph claim "every interaction is a typed PROV-O Activity carrying who did it, when, on what input, with which tool"** — NEO-AUDIT-001 shows the substrate carries 284 535 Activity nodes with **zero PROV-O edges**. The export renders the edges synthetically; the graph itself doesn't carry them. **Edit:** add the parenthetical "*(write path emits properties today; typed edges queued — see `NEO-AUDIT-001`)*" until the wiring lands; flip to unqualified once NEO-AUDIT-001 ships.

2. **§"What's in the box (today)" line on HDF5** currently reads *"A5a shipped: HdfContainer create/read/delete + opt-in `hdf` compose profile + HTTP Basic Phase 1 auth … the per-DataObject reference, the byte-identical download fallback, the permission bridge, and the shepard-API-key-to-h5pyd-bearer-token relay are queued (A5b–A5e)"*. A5b + A5d shipped per plugin-design audit; A5c + A5e still queued. **Edit:** update the parenthetical to "A5b + A5d shipped; A5c (HdfReference per-DO anchor) + A5e (h5pyd token relay) + frontend viewer queued."

3. **§"What's in the box (today)" line on SpatialDataReference** reads *"geo / spatial geometry, stored in PostGIS (optional feature toggle)"* — the plugin-design audit shows no frontend viewer, no map-tile endpoint, no MapLibre integration shipped. **Edit:** append "*(backend complete; map viewer + vector-tile endpoint in PLUGIN-SPATIAL-AUDIT-003)*" so a researcher reading the vision doesn't expect to see a map on the SpatialDataReference detail page.

4. **§"Where it's going" near-horizon line 2 on HDF5** is correctly positioned (A5 series); no edit needed — the substrate audit confirms the shape.

5. **§"The cross-cutting features" line on License + access rights (LIC1)** is up-to-date with what the IME persona confirms on the wire. No edit.

The vision doc's overall "data forging" metaphor is genuinely
load-bearing for the next year's audits — every persona audit and
every substrate audit references "snapshot chain IS the provenance".
That claim's truth-value depends on NEO-AUDIT-001 landing; preserve
the metaphor, qualify the f(ai)²r mechanics until the edges exist.

---

## §9 — Single-page operator card

```
SHEPARD ARCHITECTURE STATE — 2026-05-24 (nuclide deploy)
=========================================================

SUBSTRATES (6)
--------------
TimescaleDB 2.24.0 / PG 16.11 — 1 hypertable, 29 chunks, 22 compressed
  (23× ratio), 81 M rows, 2.7 GB. Health: GREEN. Watch: TS-AUDIT-002
  (COPY ingest), TS-AUDIT-006 (continuous aggregates).
Postgres + PgBouncer 1.25.1 — 6 non-TS tables, 3 MB; pool 20/200.
  Health: AMBER (no backup, healthcheck spam, no pg_stat_statements).
  Watch: PG-AUDIT-001 (backup), PG-AUDIT-002 (healthcheck).
MongoDB 8.0.4 — 21 collections, 758 MB. fs.chunks = 99 % of weight
  (1 file = 60 %). Health: AMBER (N+1 SD search, no validators).
  Watch: SD1, MONGO-AUDIT-001.
Garage 1.0.1 (S3) — 1 bucket, 24 objects, 13.6 MiB; cap 1 GB on a
  464 GB-free host. Health: AMBER (capacity, no backup, no admin_token).
  Watch: GARAGE-AUDIT-001 (capacity), FS1-ROUTING-* (routing bypass).
Neo4j 5.26 community — 374k :Resource, 284k :Activity (zero edges),
  17k :DataObject, 87 constraints, 115 indexes. Health: AMBER
  (PROV-O edges unwired, EAV on DO, supernode confirmed).
  Watch: NEO-AUDIT-001 (PROV), NEO-AUDIT-005 (EAV).
PostGIS 16-3.5 — separate PG instance, unaudited at schema layer.
  Health: UNKNOWN. Watch: §7 next-dispatch.

CONTAINERS (13)
---------------
shepard-core (10): backend / frontend / pgbouncer / neo4j / mongodb
  / timescaledb / keycloak / mongoexpress / caddy / garage.
  10/13 lack mem_limit. 8/10 lack healthcheck. 5 services on :latest
  (keycloak, pgbouncer, mongo-express, dozzle, alloy, arcane).
ops-side (3): alloy, dozzle, arcane (third-party).

KEY KNOBS
---------
SHEPARD_STORAGE_PROVIDER=s3        (registry honoured by 1 of 3 paths)
NEO4J_PLUGINS=["n10s"]             (APOC missing despite allowlist)
shared_preload_libraries=timescaledb   (pg_stat_statements absent)
shepard.provenance.capture-reads=false (read-path PROV opt-in)
quarkus.datasource.spatial.* → postgis  (separate PG instance)

KNOWN GAPS (CRITICAL)
---------------------
1. No backups configured for ANY substrate (PG-001 + GARAGE-003).
2. File-routing registry bypassed in 2 of 3 write paths
   (FS1-ROUTING-FIX-FORWARD + NEO-AUDIT-002).
3. PROV-O Activity edges unwired (NEO-AUDIT-001) — 284k orphan nodes.
4. 11 services without mem_limit; 8 without healthcheck.
5. Public hostname does not expose API
   (CADDY-API-PASSTHROUGH-2026-05-24) — Digital Native blocker.

PERSONA SCORES (2026-05-24)
---------------------------
Reluctant Senior  — "not yet, but the gap shrank"
IME / AQE         — 2 PASS / 4 PARTIAL / 4 GAP / 2 FAIL of 10 clauses
Digital Native    — 7.5 / 10 (down from 8 / 10 prior; new bugs)

DEPTH (read these audits for substrate detail)
----------------------------------------------
ts-design-audit-2026-05-24.md
postgres-pgbouncer-substrate-audit-2026-05-24.md
mongodb-substrate-audit-2026-05-24.md
file-storage-routing-audit-2026-05-24.md
garage-and-docker-stack-audit-2026-05-24.md
neo4j-n10s-design-audit-2026-05-24.md
persona-{reluctant-senior, manufacturing-quality, digital-native}-2026-05-24.md
plugin-design-audit-2026-05-24.md
```

---

## External sources (already grounded in the source audits)

- W3C PROV-O — `wasAssociatedWith` / `used` / `wasGeneratedBy` typed predicates: https://www.w3.org/TR/prov-o/#wasAssociatedWith
- Neo4j supernode handling — secondary-index pattern: https://neo4j.com/developer/kb/understanding-the-design-of-supernodes/
- TimescaleDB schema-design + continuous aggregates: https://docs.timescale.com/use-timescale/latest/continuous-aggregates/
- TimescaleDB write-data best practice (COPY > batched VALUES): https://docs.timescale.com/use-timescale/latest/write-data/insert/
- PostgREST (BSD-2, Tables plugin substrate adoption candidate): https://postgrest.org/
- Postgres row-level security: https://www.postgresql.org/docs/current/ddl-rowsecurity.html
- PostGIS: https://postgis.net/
- MapLibre GL JS (OSS Mapbox GL fork): https://maplibre.org/
- HSDS (H5 Group HDF5 REST server): https://github.com/HDFGroup/hsds
- h5wasm (WASM HDF5 browser reader): https://github.com/usnistgov/h5wasm
- pg_stat_statements: https://www.postgresql.org/docs/16/pgstatstatements.html
- APOC bundled-since-5.7: https://neo4j.com/labs/apoc/5/installation/
- BibTeX `@dataset` entry type (biblatex): https://www.bibtex.com/e/dataset-entry/
- Infosys "Digital Thread for Non-Conformities in Aerospace" (IME persona §"NCR routing"): https://www.infosys.com/engineering-services/insights/documents/digital-thread-non-conformities.pdf
- Garage S3 (storage substrate of record): https://garagehq.deuxfleurs.fr/

---

## Notes for the next synthesis round

- The "substrate is ahead of integration" pattern (AP-X12 and the §1 list) is the most actionable cross-cutting insight; the audit-fleet that catches *this* class of defect best is the **wiring auditor** — sit between the substrate audit and the persona audit, ask "does the API call honour the registry / write the edge / read the validator?". Worth standing up as a recurring role.
- The five Pass-1 rows + the three Pass-2 dependencies fully cover the substrate-side critical items. The Pass-3 work is where the architectural posture decisions land; treat that quarter as a design-doc quarter, not a code-quarter.
- The IME persona's `shepard-plugin-quality` proposal and the audit-fleet gap on FAIR steward re-walk together suggest the **next quarter's persona dispatch** is two: (1) the dispatched-but-unfiled FAIR steward, (2) an audit-readiness reviewer with the LIC1 + completeness + Cite-this surface in hand.
