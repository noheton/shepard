---
stage: deployed
last-stage-change: 2026-05-28
---

# Implicit Design Principles — Candidate Discovery

*Agent findings — 2026-05-27. These are patterns observed recurring across the
codebase and design docs that are not yet explicitly named in CLAUDE.md. Each
candidate is grounded in file-level evidence, not inference.*

**Promotion status (verified 2026-05-28):** All 10 principles are now codified as
`## Always:` rules in [`CLAUDE.md`](../../CLAUDE.md):

| # | Principle | CLAUDE.md anchor |
|---|---|---|
| 1 | Best-Effort Secondary Writes | "secondary writes are fire-and-forget" |
| 2 | Fail-Soft Registry | "registries are fail-soft" |
| 3 | Runtime-Mutable Config Singleton | "surface operator knobs in the admin config" |
| 4 | Schema-Free Additive Extension | "schema changes are additive and nullable" |
| 5 | Auditing-as-Graph | "the audit trail is a graph, not a log" |
| 6 | HTTP Header as Cross-Cutting Context Channel | "cross-cutting context travels in HTTP headers, not request bodies" |
| 7 | Capability-Slot Indirection | "resolve capabilities through slots, not class names" |
| 8 | appId as Universal Cross-Substrate Handle | "every persisted entity carries a single stable shepardId" |
| 9 | Skip-Capture Handoff | "handlers that record their own Activity hand off skip-capture" (added 2026-05-28) |
| 10 | Evolve in New Namespace, Never Mutate Old | "evolve in a new namespace; never mutate an existing one" |

This doc remains as the **evidence ledger** — each principle below cites the
specific code instances that motivated the rule. Reviewers can chase a rule back
to its observed pattern when judging edge cases.

---

## Principle 1 — Best-Effort Secondary Writes

**User perspective:** When you upload data or annotate a DataObject, the operation never fails because the audit trail had a hiccup. Provenance recording, quality scoring, and HMAC chain stamping all happen in the background — if one of them trips, you still get your data saved, and the system logs a warning so an admin can investigate. The audit record catches up; your operation doesn't wait for it. You will never see a 500 error that says "your upload failed because the provenance write timed out."

**Pattern observed:** Every "observability" write — provenance Activity recording,
HMAC chain stamping, AI provenance capture, plugin onRegister hooks — is wrapped in
a `try/catch` that logs at WARN/DEBUG on failure and allows the primary request to
succeed. The catch block never rethrows. The comment "Provenance is observability;
never block the request on it" appears verbatim in at least two independent classes.

**Why:** The system's observability layer (PROV-O graph, HMAC audit chain, AI
traceability) must never become a availability dependency for the data operations it
decorates. A provenance write failure that kills a data mutation would make the
audit layer an adversary. The secondary write is valuable but expendable; the primary
operation is not.

**How to apply:**
- Any new cross-cutting observability write (audit trail, metrics, semantic
  similarity index, quality score) goes in a try/catch that logs on failure and
  returns a null/empty result.
- The caller may check the result (e.g., to include the Activity appId in a
  response header) but must not branch on it for correctness.
- When a secondary write is truly load-bearing (e.g., enforcing uniqueness), it is
  not a secondary write — it belongs in the primary transaction path with normal
  exception propagation.

**Instances:**
- `backend/src/main/java/de/dlr/shepard/provenance/services/ProvenanceService.java`
  — entire `record()` body wrapped in try/catch; catch logs at DEBUG and returns null.
  Comment: "Provenance is observability; never block the request on it."
- `backend/src/main/java/de/dlr/shepard/v2/ai/AiProvenanceCapture.java`
  — every public method wraps in try/catch; same comment verbatim.
- `backend/src/main/java/de/dlr/shepard/plugin/PluginManifest.java`
  — `onRegister()` invocations in the registry loop catch per-plugin exceptions so one
  failing plugin does not abort the others.

---

## Principle 2 — Fail-Soft Registry

**User perspective:** If a plugin or AI provider isn't installed or configured, Shepard still starts up and the rest of the system works normally. You might see "AI quality scoring unavailable" on a DataObject instead of a score — but you can still upload data, browse collections, run searches, and annotate. The missing capability is surfaced as a clear message, not as a crashed service that takes everything else down with it. An admin can add the missing plugin at runtime without a restart.

**Pattern observed:** Extension point registries (`AiRegistry`, `MinterRegistry`,
`PluginRegistry`) never throw during startup discovery. When zero implementations are
found, or when a conflict is detected (two beans claim the same transport id), the
registry logs a WARN and exports an empty Optional from its resolution methods rather
than failing fast. HTTP callers receive a 503 or a degraded response; the process stays
up.

**Why:** Plugin implementations are optional by definition; their absence at a given
deployment must not make core Shepard inoperable. Discovery at startup is the worst
time to crash — it prevents the admin from even reaching the UI to diagnose the
problem. Surfacing degraded state at call time (503 with a human-readable reason) is
far more operator-friendly than a startup abort.

**How to apply:**
- Every new SPI registry follows the `AiRegistry` shape: CDI bean discovery, indexed
  by id + by capability, conflicts logged as WARN (not fatal), resolution returns
  `Optional.empty()` when no match.
- Startup log messages state the degradation clearly so an operator reading container
  logs understands what's missing without touching the UI.
- HTTP endpoints backed by a registry convert `Optional.empty()` to 503 with a
  message naming the missing capability; they never NPE.

**Instances:**
- `backend/src/main/java/de/dlr/shepard/spi/ai/AiRegistry.java`
  — "Failure posture: never fail-fast. Every degraded state surfaces as a WARN at
  startup plus an empty Optional return from the resolution methods." Comment also
  notes shape mirrors `MinterRegistry`.
- `backend/src/main/java/de/dlr/shepard/publish/minter/MinterRegistry.java`
  — 4 degradation states (no CDI beans, no config, wrong transport id, provider
  exception) all end in WARN + 503, not startup failure.
- `backend/src/main/java/de/dlr/shepard/plugin/PluginRegistry.java`
  — per-plugin `onRegister` exceptions caught; the loop continues.

---

## Principle 3 — Runtime-Mutable Config Singleton

**User perspective:** An admin can change Shepard's behaviour — rate limits, AI endpoints, ontology URLs, retention windows — without restarting the service or touching a config file. They go to the admin panel, flip the knob, and it takes effect immediately. The default is baked in when the system first starts so there's nothing to configure out of the box; the runtime override is available when the deployment needs to differ from the default. No restart means no downtime for your active analyses.

**Pattern observed:** Every feature that needs an operator-tunable knob gets a
single `*Config` Neo4j node (`HasAppId`, single instance per deployment), a
`seedIfNeeded()` method called from `@Observes StartupEvent`, `effective*()` methods
that return the runtime field if non-null and fall back to a deploy-time
`application.properties` default, and a `PATCH /v2/admin/<feature>/config` endpoint
using RFC 7396 merge-patch semantics. The pattern is explicitly referenced in the
JavaDoc of each implementing class.

**Why:** Operators legitimately need to tune runtime knobs (rate caps, URL
overrides, toggles) without restarting the service. Deploy-time-only config forces a
deploy cycle for routine tuning and prevents operators from observing live effects.
The seed-on-startup pattern means every deployment has a valid singleton from first
boot; the effective*() indirection keeps the runtime value sovereign while preserving
the properties file as a baked-in default for IaC.

**How to apply:**
- Every new feature with an operator-tunable field gets this four-part shape: entity
  (`HasAppId`), service (`seedIfNeeded` + `effective*` + `patch`), REST resource
  (`GET` + `PATCH /v2/admin/<feature>/config`), placeholder entry in the frontend.
- Null field value in the singleton means "use the deploy-time default"; PATCH with
  null clears a previously set runtime override.
- `seedIfNeeded()` must be idempotent and must not throw (warn-and-continue) because
  it can be called before the DB is fully warm.
- CLI parity under `shepard-admin <feature> {status,set-field,...}` per the L1
  baseline.

**Instances:**
- `backend/src/main/java/de/dlr/shepard/v2/admin/sqltimeseries/services/SqlTimeseriesConfigService.java`
  — canonical implementation; `seedIfNeeded()`, `effectiveMaxRows()`,
  `effectiveMaxDuration()`, RFC 7396 `patch()`; Javadoc cites "A3b / N1c2 / UH1a /
  ROR1 pattern."
- `backend/src/main/java/de/dlr/shepard/v2/admin/ror/services/InstanceRorConfigService.java`
  — same pattern; `ror.api-url` deploy-time default, runtime override via PATCH.
- `backend/src/main/java/de/dlr/shepard/v2/admin/sqltimeseries/entities/SqlTimeseriesConfig.java`
  — entity Javadoc: "A single instance exists per deployment. Fields are all nullable;
  null means 'use the deploy-time application.properties default.'"

---

## Principle 4 — Schema-Free Additive Extension

**User perspective:** When a new feature is deployed — say, a quality score field on TimeSeries, or a license field on Collections — your existing data doesn't break and doesn't require a migration that locks the DB for hours. Old DataObjects simply don't have the new field yet; the system treats that as "not set." You can start filling it in gradually without touching anything from before. The system never forces you to backfill 10,000 historical records before you can use a new feature.

**Pattern observed:** New fields on existing Neo4j node entities are added without a
schema migration: they carry no `@Property` annotation (or one without a `NOT NULL`
constraint), have a Javadoc comment noting "No migration required — absent on
pre-feature rows reads as null," and the service code treats null as "feature not yet
observed on this row." New Postgres columns use `ALTER TABLE ... ADD COLUMN IF NOT
EXISTS` with a nullable or defaulted type. No backfill is done on historical rows
unless the field needs to be queryable for correctness.

**Why:** Neo4j's schema-optional property model makes this essentially free for
graph entities. Adding a NOT-NULL constraint or a required field forces either a
bulk backfill (expensive, risky on a live DB) or a startup migration (blocks startup).
Nullable = null-means-not-present keeps the migration surface minimal and makes
new features incrementally adoptable on deployments that have existing data.

**How to apply:**
- New fields on existing node entities default to nullable unless they are truly
  mandatory for all existing rows.
- When a field is used for filtering/indexing, add the index migration in the same
  PR but not a data backfill unless the query would be wrong without it.
- Document the "absent = null = pre-feature" semantic in the field's Javadoc.
- Avoid adding required constructor parameters to node entities — OGM instantiates
  via reflection; required params break on existing graph rows.

**Instances:**
- `backend/src/main/java/de/dlr/shepard/context/collection/entities/DataObject.java`
  — `provenanceMode` (PROV1j): Javadoc "No migration required — null on pre-PROV1j
  rows"; `typedPredecessorsJson` (PROV1k): nullable JSON column.
- `backend/src/main/java/de/dlr/shepard/provenance/entities/Activity.java`
  — `sourceMode`, `agentId`, `ledgerAnchor`, `mirroredUserAppId` all added without
  schema migration; each nullable; pre-feature rows read them as null.
- `backend/src/main/resources/neo4j/migrations/V85__FAIR2_FAIR3_fields.cypher`
  — additive index-only migration (no backfill); comment explains absent properties
  are treated as empty at query time.

---

## Principle 5 — Auditing-as-Graph

**User perspective:** When you look at a DataObject and ask "who touched this, when, and why?" — Shepard doesn't give you a flat log file. It gives you a graph you can actually query: *"show me all changes User A made to this collection after the June test campaign"*, *"which AI models annotated these measurements?"*, *"what did TR-004's anomaly investigation chain look like, step by step?"* The audit history is stored the same way as the data itself, so it works with all the same query tools — SPARQL, Cypher, the provenance panel in the UI. You never have to correlate two separate systems (data here, logs somewhere else).

**Pattern observed:** The audit trail is not a log file, not an append-only SQL
table, and not a set of `updatedAt` timestamps — it is a first-class subgraph in
Neo4j. Each `:Activity` node carries full PROV-O edges (`WAS_ASSOCIATED_WITH` →
`:User`, `GENERATED` → persisted entity, `USED` → consumed entity) that are
queryable via Cypher or SPARQL. The provenance surface can emit the same data as
JSON-LD, PROV-N, metadata4ing flavour, or raw JSON depending on the `Accept` header.
The HMAC chain (`auditHmac`/`auditPrevHmac`) makes the graph tamper-evident without
leaving the graph substrate.

**Why:** Graph representation makes the audit trail intrinsically queryable without
ETL: "give me all Activities where USER_A touched DataObject B after date X" is one
Cypher query. PROV-O alignment means the same query can be expressed in SPARQL and
answered by the n10s semantic layer. Storing provenance in the same substrate as the
data it describes collapses the two-store problem (data in Neo4j, audit in a separate
log sink) and makes the audit surface a first-class citizen of the semantic layer.

**How to apply:**
- New entity types that are audit-worthy get PROV-O edges wired by `ActivityDAO`
  (the `wireEdges()` method pattern): `GENERATED` to the result entity, `USED` to
  the input entities.
- New endpoints that need richer audit metadata add nullable fields to `:Activity`
  (schema-free additive, Principle 4) rather than a separate log table.
- When a feature needs tamper-evidence, extend the HMAC chain via
  `HmacChainService.stamp()` (best-effort secondary write, Principle 1).
- Content negotiation on `/v2/provenance/*` endpoints supports the existing four
  formats; new formats are added as `MediaType` constants in the resource class.

**Instances:**
- `backend/src/main/java/de/dlr/shepard/provenance/daos/ActivityDAO.java`
  — `wireEdges()` writes `WAS_ASSOCIATED_WITH`, `GENERATED`, `USED` relationship
  nodes; called by `ProvenanceService.record()`.
- `backend/src/main/java/de/dlr/shepard/v2/provenance/resources/ProvenanceV2Rest.java`
  — `Accept: application/ld+json` → JSON-LD, `text/provenance-notation` → PROV-N,
  `application/vnd.metadata4ing+json` → m4i flavour dispatch.
- `backend/src/main/java/de/dlr/shepard/provenance/entities/Activity.java`
  — `auditHmac`/`auditPrevHmac`/`secretVersion` fields enabling the HMAC chain
  anchored in the graph node itself.

---

## Principle 6 — HTTP Header as Cross-Cutting Context Channel

**User perspective:** When a script, an MCP tool, or a CI pipeline calls Shepard's API, it can add a small flag in the HTTP header saying "this was AI-generated" or "this call is on behalf of user X." Shepard records that in the audit trail automatically — without the API endpoint needing to change shape. So when you look at the provenance for an annotation, you see not just *what* changed, but *who drove it* (human / AI / collaborative). This is what makes the EU AI Act Art. 50 disclosure work: the AI's fingerprint on every action, without any extra fields in the data format.

**Pattern observed:** Features that need to pass context across the HTTP boundary
without polluting the request body use custom request headers. The pattern appears
for: AI agent identity (`X-AI-Agent` → `sourceMode = "ai"` in Activity), source-user
enrichment in proxy scenarios (`X-Source-User-AppId`, `X-Source-User-Username`),
client version negotiation (`X-Shepard-Client-Version`), and provenance mode
selection (`X-Provenance-Mode` response header). These are read once at filter/
interceptor level and surfaced as request-scoped bean state for downstream services.

**Why:** Request body shapes are versioned and domain-specific; injecting cross-cutting
context (who made the call, what mode, what agent) into them couples the body schema
to infrastructure concerns. Headers are orthogonal to the body, naturally stripped by
reverse proxies when appropriate, and trivially extended without a body schema version
bump. Reading them in a filter or interceptor keeps the service layer clean — a
service just sees `currentRequest.sourceMode()`, not `requestBody.xAiAgent`.

**How to apply:**
- New cross-cutting context (agent identity, delegation chain, feature toggle
  override) goes in a custom `X-Shepard-*` header, documented in
  `aidocs/16-dispatcher-backlog.md` with its consuming filter.
- The filter/interceptor reads the header, validates it, and sets a request-scoped
  field or CDI `@RequestScoped` bean for downstream consumption.
- Never read a cross-cutting context header directly in a REST resource method —
  only in the filter layer.
- Response headers (e.g., `X-Provenance-Mode`, `X-Activity-AppId`) follow the same
  convention for context the caller may want to observe.

**Instances:**
- `backend/src/main/java/de/dlr/shepard/provenance/filters/ProvenanceCaptureFilter.java`
  — reads `X-AI-Agent`, `X-Source-User-AppId`, `X-Source-User-Username`; derives
  `sourceMode` from header presence; sets `mirroredUserAppId` on the Activity.
- `backend/src/main/java/de/dlr/shepard/v2/admin/sqltimeseries/resources/SqlTimeseriesV2Rest.java`
  — `X-Shepard-Client-Version` read at filter level for client version mismatch
  detection (CG2a).
- `backend/src/main/java/de/dlr/shepard/provenance/services/ProvenanceService.java`
  — `sourceMode` field on Activity populated from the header-derived request context,
  satisfying EU AI Act Art. 50 machine-readable disclosure.

---

## Principle 7 — Capability-Slot Indirection

**User perspective:** As a researcher at DLR, you connect Shepard to the SAIA AI service. A colleague at another institute connects their Shepard to an Ollama instance running locally. A third deployment uses OpenAI. The feature — anomaly detection, annotation suggestions, quality scoring — works the same way in all three. An admin changes which AI backend fills each slot from the admin panel, with no code change and no restart. What the feature *does* is defined in Shepard; who *delivers* it is an operator decision.

**Pattern observed:** When a feature can be served by multiple competing
implementations (LLM providers, PID minters, notification transports), the code
never resolves the implementation directly by class name. Instead, it requests a
named capability slot (e.g., `AiCapability.TEXT`, `MinterRegistry.ORCID`) from a
registry. The registry resolves the slot to an admin-configured transport id at
runtime. Changing which provider serves a slot is a PATCH to a config endpoint, not
a code change or redeploy.

**Why:** Hard-coding a provider class name in a service couples the feature to a
deployment choice that changes across environments (SAIA at DLR, OpenAI in a
developer laptop, Ollama offline). Capability slots decouple the "what capability
do I need" (code concern) from "which provider delivers it in this deployment"
(operator concern). The same binary can serve multiple deployment profiles by
changing config alone.

**How to apply:**
- When a new feature needs an AI, minting, or notification capability, define it as
  a new `AiCapability` / `MinterType` / `NotificationTransport` enum value (extend,
  don't add a new parallel system).
- The feature calls `aiRegistry.resolve(AiCapability.MY_SLOT)` and handles
  `Optional.empty()` gracefully (fail-soft, Principle 2).
- A new `:AiCapabilityConfig` singleton is seeded for the new slot on startup
  (runtime-mutable config singleton, Principle 3).
- The admin UI exposes a picker for each slot's current transport id.

**Instances:**
- `backend/src/main/java/de/dlr/shepard/spi/ai/AiCapability.java`
  — 8-slot enum (TEXT, FAST_TEXT, IMAGE_GEN, VISION, EMBEDDING, STRUCTURED,
  TRANSCRIPTION, MODERATION); each slot independently configurable.
- `backend/src/main/java/de/dlr/shepard/spi/ai/AiRegistry.java`
  — `resolve(AiCapability)` → `Optional<LlmProvider>`; admin sets which transport
  id fills each slot via `PATCH /v2/admin/ai/capabilities/{slot}`.
- `backend/src/main/java/de/dlr/shepard/publish/minter/MinterRegistry.java`
  — same shape for PID minters; `shepard.publish.minter` property selects the
  active transport id at deploy time; PATCH can override at runtime.

---

## Principle 8 — appId as Universal Cross-Substrate Handle (future name: shepardId)

**User perspective — clarification:** These are two separate things. The `urn:shepard:*` predicate namespace is about how annotation vocabulary *terms* are named (e.g., `urn:shepard:spatial:axis`) — that is captured in the organizing ontology / annotation preselection principle. This principle is about a completely different thing: the stable UUID entity identity that every piece of data carries. Currently called `appId` in the Java code; a coordinated rename to `shepardId` is planned (tracked in memory `feedback_appid_to_shepardid.md`).

**User perspective:** Every entity in Shepard — across every storage system — carries the same single UUID. When you write a Python script to fetch a TimeSeries, annotate a DataObject, or query via the MCP tools, you use one ID and it works everywhere. You never need to know "this is the Neo4j node ID" vs "this is the database primary key" — those are internals. The `shepardId` is the stable public handle. The timeseries 5-tuple (measurement + device + location + symbolicName + field) is exactly the cost of *not* following this principle early enough — and the ongoing TS-CORE-SCHEMA-01 migration fixes it.

**Pattern observed:** Every persisted entity across all five substrates (Neo4j,
TimescaleDB, MongoDB, PostGIS, Garage/MinIO) carries the same UUID v7 `appId`
field as its application-layer primary key. Semantic annotations, provenance edges,
MCP tool arguments, and cross-substrate join queries all use `appId` exclusively —
never database-internal IDs (Neo4j node IDs, MongoDB ObjectIds, TimescaleDB
serial PKs). The `HasAppId` marker interface enforces this at compile time for
Java entities.

**Why:** Neo4j internal node IDs are ephemeral (reassigned after restore/migration).
MongoDB ObjectIds, TimescaleDB serials, and S3 paths are substrate-specific. A
single UUID v7 that travels with the entity across every substrate makes
cross-substrate joins, REST resource paths, MCP tool calls, and semantic annotation
attachment all speak the same language. UUID v7 (time-ordered) keeps index locality.
The ongoing timeseries 5-tuple → appId migration (TS-CORE-SCHEMA-01) is precisely
the "we violated this principle and paid the price" case.

**How to apply:**
- Every new persisted entity type, in any substrate, gets an `appId` UUID v7 field
  minted at creation time.
- For Java Neo4j entities, implement `HasAppId`; `GenericDAO.createOrUpdate()` mints
  automatically when null.
- For Postgres/TimescaleDB, add an `appId UUID NOT NULL` column with a unique index
  via `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`.
- REST resource paths use `appId` as the path parameter, not the substrate PK.
- MCP tool arguments accept `appId` strings; internal resolution to substrate keys
  is an implementation detail of the DAO layer.

**Instances:**
- `backend/src/main/java/de/dlr/shepard/common/identifier/HasAppId.java`
  — marker interface; every Neo4j entity implements it.
- `backend/src/main/java/de/dlr/shepard/common/neo4j/daos/GenericDAO.java`
  — `createOrUpdate()` mints `AppIdGenerator.next()` (UUID v7) when `appId` is null;
  also increments `revision` on `VersionableEntity` subtypes.
- `aidocs/platform/87-timeseries-appid-migration.md`
  — the 5-tuple (measurement/device/location/symbolicName/field) is the acknowledged
  violation; the migration path to single `appId` per channel is the corrective.

---

## Principle 9 — Skip-Capture Handoff

**User perspective:** When you annotate a DataObject with an AI-suggested term, the provenance record shows exactly one entry: "AI model X suggested this annotation at confidence 0.87, user confirmed it." You don't see two duplicate entries — one generic "annotation written" and one specific "AI annotation written." The system knows when a feature is recording its own richer provenance and steps back, so your activity feed stays clean and meaningful rather than double-counting every action that an AI touches.

**Pattern observed:** The `ProvenanceCaptureFilter` automatically records one
`:Activity` per mutating 2xx request. But some handlers (annotation endpoints, AI
invocation endpoints) need to record a richer or differently-typed Activity with
additional PROV-O fields that the filter cannot see. These handlers set the request
property `PROP_SKIP_CAPTURE` *after* recording their own Activity via
`ProvenanceService.record()`, suppressing the filter's duplicate capture. The two
paths share the same `ProvenanceService` but the handler owns the Activity shape.

**Why:** The filter is a least-common-denominator capture for mutations it doesn't
understand. Handlers with domain knowledge of the mutation (semantic annotation
write, AI model invocation, importer commit) need to record a more specific Activity
— with `sourceMode`, `vocabularyId`, `agentId`, `inputDataObjectAppIds`, etc. The
skip property is the handoff signal: "I already did this, and I did it better."
Without it, every annotation write would generate two Activities.

**How to apply:**
- Any REST resource that calls `ProvenanceService.record()` directly must set
  `requestContext.setProperty(PROP_SKIP_CAPTURE, true)` immediately after, before
  returning the response.
- The filter reads this property in its `aroundWriteTo` phase; if set, it skips
  the generic Activity write.
- The directly-recorded Activity must include at minimum the same fields the filter
  would have written (userId, resourcePath, httpMethod, timestamp), plus its
  domain-specific enrichment.

**Instances:**
- `backend/src/main/java/de/dlr/shepard/provenance/filters/ProvenanceCaptureFilter.java`
  — reads `PROP_SKIP_CAPTURE` in the response filter phase; skips generic write if
  set.
- `backend/src/main/java/de/dlr/shepard/v2/semantics/resources/AnnotationV2Rest.java`
  — `createAnnotation()` calls `provenanceService.record(...)` with `vocabularyId`
  and `sourceMode`, then sets `PROP_SKIP_CAPTURE`.
- `backend/src/main/java/de/dlr/shepard/v2/ai/resources/AiV2Rest.java`
  — AI invocation endpoints record an `:AiActivity` (distinct type) then set
  `PROP_SKIP_CAPTURE` to prevent the filter from also recording a generic one.

---

## Principle 10 — Evolve in New Namespace, Never Mutate Old

**User perspective:** When Shepard ships a new feature, it does not break anything that was working before. New API endpoints appear at `/v2/something-new`; old endpoints at `/shepard/api/...` don't change. New annotation vocabulary terms live under `urn:shepard:your-domain:your-role` without touching existing ontology terms. Database migrations are always additive — a new field that old rows simply don't have yet, rather than a column that breaks existing data. Scripts you wrote today still work after an upgrade; annotations you made last year don't conflict with new vocabulary the system learned this month; if you're running an older client against a newer server, it still speaks the same language for everything it already knew.

**Pattern observed:** Every extension point — API paths, migration files, property
keys, semantic predicates — is added in a new, additive namespace rather than
modifying an existing one. The fork's additions live at `/v2/` (not patching
`/shepard/api/`). New Neo4j migrations are `V(N+1)__*` with `IF NOT EXISTS` guards
plus a paired `V(N+1)_R__*` rollback file. New semantic predicates use
`urn:shepard:<domain>:<role>` rather than extending upstream ontology terms. New
config keys use `shepard.v2.<feature>.*` rather than patching upstream keys.

**Why:** Mutating a shared namespace creates coordination debt: upstream upgraders
must disentangle the change, clients must absorb a breaking path change, operators
must reason about mixed-version state. New namespaces let old and new coexist:
`/shepard/api/` callers are unaffected by `/v2/` additions; pre-migration rows coexist
with post-migration rows because the new field is nullable (Principle 4); old
predicates remain valid while new ones extend the vocabulary. The cost is slightly
more surface area; the benefit is zero forced upgrades.

**How to apply:**
- New endpoints go under `/v2/<resource>`, never as a new method on a
  `/shepard/api/` resource.
- New semantic predicates go under `urn:shepard:<your-domain>:<your-role>`, documented
  in the organizing ontology manifest; never reuse or extend a third-party term by
  mutation.
- New Neo4j migrations get a fresh `V(N+1)__` file with `IF NOT EXISTS` and a rollback
  twin; never edit an existing migration file after it has been applied to any
  production instance.
- New deploy-time config keys follow `shepard.v2.<feature>.<knob>`; runtime-mutable
  versions get a `:*Config` singleton (Principle 3) using the same name.

**Instances:**
- `frontend/composables/common/api/useV2ShepardApi.ts`
  — derives the `/v2/` base URL by stripping `/shepard/api` from the v1 base URL;
  the two-shelf architecture is transparent to callers of this composable.
- `backend/src/main/resources/neo4j/migrations/V92__Add_appId_constraint_InstanceRegistry.cypher`
  — new migration file with `IF NOT EXISTS` guard; paired rollback file referenced
  in the header comment.
- `/root/.claude/projects/-opt-shepard/memory/project_annotation_preselection_principle.md`
  — `urn:shepard:spatial:axis`, `urn:shepard:analysis:threshold`,
  `urn:shepard:display:colormap` as additive terms under the owned namespace,
  never patching Dublin Core or PROV-O terms.

---

## Priority Ranking

Ranked by: **(a) how much unwritten code this principle would save, (b) how often
developers could violate it by accident, (c) how hard it is to retrofit if ignored).**

| Rank | Principle | Priority driver |
|------|-----------|-----------------|
| 1 | **Principle 1 — Best-Effort Secondary Writes** | Most likely to be violated by reflex (adding a throw inside a provenance catch); hardest to retrofit because a thrown exception inside a filter propagates to the caller and the fix requires rewriting the filter contract. |
| 2 | **Principle 8 — appId as Universal Cross-Substrate Handle** | The timeseries 5-tuple is live debt from ignoring this. Every new substrate entity that skips an `appId` column creates a future TS-CORE-SCHEMA-01-style migration. |
| 3 | **Principle 4 — Schema-Free Additive Extension** | Highly likely to be violated by reflex on NOT NULL constraints; retrofit requires a live-data backfill on possibly millions of rows. |
| 4 | **Principle 3 — Runtime-Mutable Config Singleton** | CLAUDE.md partially covers this, but the seedIfNeeded + effective* + RFC-7396 implementation shape is not yet code-reviewed as a checklist. Missing it means deploy-time-only config that requires restarts. |
| 5 | **Principle 10 — Evolve in New Namespace, Never Mutate Old** | CLAUDE.md covers the `/v2/` rule but not the migration and predicate aspects; a developer adding a new semantic predicate by editing an upstream ontology term would silently violate this. |
| 6 | **Principle 2 — Fail-Soft Registry** | Important but lower-frequency: new SPIs are rare; the penalty for getting it wrong (startup abort) is highly visible and easy to fix. |
| 7 | **Principle 9 — Skip-Capture Handoff** | Narrow scope; only annotation and AI endpoints need this. But missing it creates duplicate Activity rows that are hard to clean up after the fact. |
| 8 | **Principle 7 — Capability-Slot Indirection** | Medium frequency; applies whenever a new pluggable capability is added. Violation (hard-coding a provider class) is easily spotted in review. |
| 9 | **Principle 5 — Auditing-as-Graph** | Mostly a design-level choice; developers unlikely to choose a separate log table by accident if they see existing code. |
| 10 | **Principle 6 — HTTP Header as Cross-Cutting Context Channel** | Low violation risk; developers naturally put context in the body if they don't know the pattern, but the filter-layer convention is visible in existing code. |
