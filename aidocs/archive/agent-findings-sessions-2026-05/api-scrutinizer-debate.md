---
stage: decommissioned
last-stage-change: 2026-05-23
---

# API Scrutinizer — Phase 2 Debate
**Author:** API Scrutinizer agent (cross-peer synthesis and verdict)
**Date:** 2026-05-21
**Inputs:** All 8 peer proposal docs + ecosystem context prompts from the task brief

---

## Preamble: The debate in one paragraph

After reading 80+ proposals from 8 agents, the picture is clear. The proposals cluster
into about 12 distinct logical features — most agents just named the same feature
differently and cited each other for confirmation. The overlaps are the signal:
wherever 5 of 8 agents converge independently, that feature is real and load-bearing.
The divergences are where the API lens adds value — agents proposing endpoint shapes
without thinking through auth layers, agents saying "no API change needed" when one
is required, agents sending two proposals into the same design space with incompatible
assumptions. That is what this document is about.

One foundational commitment first: **the five proposals in my own phase-1 are the
prerequisites for nearly every other proposal in this corpus**. Ancestor-chain endpoint
(5 agents) is blocked on my P2 (flat-GET). Agentic ingest (3 agents) is blocked on
my P3 (import/jobs execute). PDF auto-annotation (3 agents) is blocked on my P1
(typed container arrays). ProblemJson (all agents' MCP/SDK clients) is my P7.
Pagination consistency (all list consumers) is my P5. The rest of this debate
assumes these land first — if they don't, much of the below is moot.

---

## Top 5 I'm Championing

### 1. CHAMPION — Typed Container Arrays in DataObjectV2IO (API Scrutinizer P1, XS→S, no blockers)

Every agent's proposal chain passes through `DataObjectIO`. The live 404 bug in the
MCP server (`referenceIds` containing Neo4j edge IDs, not appIds) is the most
demonstrable broken contract in the entire API surface. The ecosystem advocate (EP-07)
independently identified it. Strategy (P12) named it as the first fix in their API
hygiene sprint. The data fix is architectural: replace `referenceIds: long[]` with
`timeseriesReferenceAppIds: string[]`, `fileReferenceAppIds: string[]`,
`structuredDataReferenceAppIds: string[]` in the v2 projection. The v1 surface stays
byte-identical. This takes 2–3 days and unlocks:
- The MCP server (working today, broken on any DataObject with containers)
- The analytics agent's LLM manifest generator (AI P9, depends on stable container pointers)
- The UX agent's ancestor-walk chain (each hop needs the container inventory of the ancestor)
- The ecosystem advocate's MCP toolset (EP-07)

This is the highest-ROI single change in the entire proposal set. It is not
glamorous. Ship it.

**API verdict:** Shape is correct. The `DataObjectListItemV2IO` precedent already
exists — this is pattern-following, not pattern-setting. One caveat: plugin-specific
container types (`videoStreamReferenceAppIds`, `hdfContainerAppIds`, `gitReferenceAppIds`)
should be included too — but only emit the arrays for installed plugins. An instance
without the video plugin should return `null` (or omit the key), not an empty array
that implies the plugin is present.

---

### 2. CHAMPION — POST /v2/import/jobs (API Scrutinizer P3, M, no AI blockers)

The validate endpoint is the most consequential dead end in the API. It produces a
`commitId` that expires in 24 hours and cannot be redeemed. The analytics agent
(AI P9 phase 1), strategy (P12 / S2), and manufacturing quality all name this CRITICAL.
I named it CRITICAL in my own findings. Five agents converge on the same gap without
being told to look for it.

**The ingest SPI architectural question** — raised in the task brief — must be
resolved here, because `POST /v2/import/jobs` is the endpoint that `shepard-plugin-ingest`
source adapters will call. My position: **`POST /v2/import/jobs` IS the universal
ingest surface for all manifest-based ingest, regardless of source adapter.** Each
source adapter (filesystem hotfolder, MQTT, S3 watch, git commit hook) converts
its source format into an `ImportManifestIO` JSON and POSTs it to `/v2/import/jobs`
exactly as the user-facing importer does. The adapter is a protocol translator; the
job endpoint is the single execution boundary.

This means `/v2/import/validate` → `/v2/import/jobs` is not "user-facing import +
plugin ingest SPI". It is the one ingest boundary for both. The plugin SPI decides
*how a manifest is generated* (per-source logic), not *how it is executed* (that
stays in core). This is the right split: core owns the execution contract; plugins
own the source-specific manifest generation.

**API shape is correct as specified** (202 Accepted + jobAppId + GET poll endpoint).
The `agentContext.generatedByAiActivityAppId` field for AI-provenance tracing is
correct and should be in the MVP shape, not deferred. One addition: a `dryRun` flag
should be in the execute body — it lets pipeline callers validate the full execution
path without committing, which is what `commitId` was trying to do but never completed.

---

### 3. CHAMPION — Unified FAIR Metadata Spine (merged from 8 agents, M total, HMC deadline 06 July 2026)

RDM P1+P2+P3+P4+P5, Strategy P1, AI P10, Ontologist P7, UX P4, Ecosystem EP-02 are
all the same proposal under different names. The shared core is:

- Four additive fields on `AbstractDataObject`: `license` (SPDX String, nullable),
  `accessRights` (enum OPEN/EMBARGOED/RESTRICTED, nullable), `embargoEndDate`
  (ISO-8601 String, nullable, required when EMBARGOED), `createdByOrcid` (String,
  stamped from `User.orcid` at creation time), `fundingReferences` (List<String>)
- `GET /v2/collections/{appId}/metadata-completeness` returning a scored checklist
- A publication gate at score ≥ 60 (configurable)

The convergence across 8 independent agents is the strongest confidence signal in
the entire corpus. The implementation is straightforward (additive Neo4j fields,
no migration, one new service + endpoint). The HMC Project Call 2026 deadline
**06 July 2026** (six weeks from today) makes this the single most time-critical
item in the priority stack. The Unhide feed currently emits `schema:license` from an
instance-default fallback with no entity-level source; that is architecturally wrong
and will fail an HMC validator run.

**API verdict on field placement:** RDM P4 proposes storing `fundingReferences` as
`@Properties`-serialized JSON using the `||` delimiter pattern. That is correct —
it avoids a new entity and migration while keeping the field queryable. The value type
`{funderName, funderRor, grantId, grantTitle}` is minimal and sufficient for DataCite
Metadata Schema 4.5.

**API verdict on completeness score endpoint:** The check IDs (string slugs like
`"license"`, `"orcid"`, `"pid"`) must be stable and documented — frontend and
MCP tooling clients will key off them. Do not change check IDs between releases
without a deprecation notice. Score weights should be admin-configurable via
`:PublisherConfig` — different funding bodies weight fields differently.

---

### 4. CHAMPION — TS-IDa + TS-IDb (API Scrutinizer P9, M, no blockers)

The 5-tuple channel identity (`measurement`, `device`, `location`, `symbolicName`,
`field`) is not a developer ergonomics problem — it is a data integrity problem for
ML pipelines. Any script that assembles a 5-tuple breaks silently on channel rename.
The live-window endpoint currently loads ALL channels for a container into JVM memory
and filters in Java (full table scan per poll). TS-IDb converts that to a single index
lookup. The `selectedChannels` field in `TimeseriesContainerChartViewIO` stores
pipe-separated 5-tuple strings that are brittle to rename.

Three agents (API, Strategy P5, AI) call this CRITICAL infrastructure that multiplies
the value of every timeseries feature. The design doc (`aidocs/87`) is written; the
migration is one Cypher line. TS-IDa (mint UUIDs) and TS-IDb (expose in responses)
are zero-risk additive changes.

**API verdict:** The live-window endpoint fix (`?timeseriesAppId=` parameter on
`GET /v2/timeseries-containers/{containerAppId}/channels/live-window`) should be
shipped with TS-IDb in the same PR. The current 5-tuple filter stays as a deprecated
alias for one release cycle — do not remove it in the same PR as the introduction.
The `selectedChannels` format migration (pipe-separated → appId array) is a Neo4j
data migration and should be idempotent — run it as part of the startup migration
chain, not as a manual operator step.

---

### 5. CHAMPION — ProblemJson + OpenAPI Tags + Pagination Envelope (API Scrutinizer P7+P8+P5, S total)

These three are pure hygiene but they have outsized impact on every external caller.

**ProblemJson (P7):** Register one `ExceptionMapper<WebApplicationException>` in the
core CDI context. All plugins inherit it automatically. The nine resources that
currently hand-roll error strings (named in my original findings) throw
`WebApplicationException` instead and the mapper handles serialization. This is
2 days of work that fixes every AI agent's error-parsing failures at once.

**OpenAPI tags (P8):** Mechanical rename of internal task codes to human-readable
names. Generated SDK class names go from `TagTsLive1Api` to `TimeseriesLiveWindowApi`.
One commit. The MCP server generates tool names from OpenAPI tags — readable names
directly improve LLM tool-selection accuracy, which the analytics agent explicitly
called out.

**Pagination envelope (P5, partial):** Add `totalCount` and `hasMore` to all list
responses. The status filter on DataObject list (`?status=`) is the most impactful
single field here — it makes the client-side filter bug (UX CRITICAL, curator sees
only the current page of results matching status) disappear. Every other list endpoint
fix is a one-liner per endpoint: add `totalCount: repository.count(...)` to the
wrapper.

**API verdict on ProvenanceRest migration:** `?limit` → `?page`+`?size` on
`ProvenanceRest.listActivities` should keep `?limit` as a deprecated alias for two
release cycles (not one) because provenance callers are more likely to be
programmatically generated than hand-crafted.

---

## Top 3 I'm Challenging

### 1. CHALLENGE — Ecosystem EP-03 (Public Collection Landing Page)

The ecosystem advocate (EP-03) claims this is "not new data work — just a rendering
and auth-bypass surface." That is wrong on three counts:

**Auth layer problem:** A `/public/collections/{appId}` route that bypasses normal
authentication is a *new permission tier*, not a bypass. The current PermissionsService
has Read/Write/Admin. There is no "anonymous public read" tier. Adding one touches the
permission model, the OIDC filter chain, and the collection visibility policy — none
of which are small changes. The `accessRights = OPEN` check on a public route means
the auth middleware must evaluate `accessRights` before the OIDC filter fires, which
inverts the current filter order.

**CORS/CSP problem:** The embed card (`<iframe>`-embeddable, claimed S in effort)
requires CORS headers on the embed route and a `Content-Security-Policy` that allows
framing from arbitrary origins. If the Caddy reverse proxy sets a blanket
`X-Frame-Options: DENY` (common security default), the embed breaks at the
infrastructure layer, not the application layer. This is not surfaced in the proposal
at all.

**Data classification problem for MFFD:** The MFFD data is DLR industrial IP. A
public landing page that exposes metadata (even without payloads) for `accessRights ≠ OPEN`
collections — or exposes collection *existence* via 404 vs. 403 behavior — leaks
structural information about internal projects. The proposal needs a threat model.

**Verdict:** REDIRECT to a security design doc before implementation. The core idea
(public landing page for published collections) is sound and competitively important.
The implementation path is more complex than EP-03 acknowledges. The simpler interim:
add a `GET /v2/collections/{appId}/public-metadata` endpoint (requires auth, returns
only the safe subset: name, description, license, PID) and let the KIP plugin's PID
landing page serve the public face. No new permission tier needed.

---

### 2. CHALLENGE — Strategy P7 (Snap Dashboards MVP / "Chart from description")

Strategy P7 proposes a "Chart from description" button that sends free text to
`shepard-plugin-ai` STRUCTURED capability and gets back a Vega-Lite spec. The API
shape is missing entirely. "Text → chart spec → render" is described as "deliberately
narrower than AI1e" but the narrowing is not specified. What endpoint does the frontend
call? What is the request body? What is the response envelope? Does the Vega-Lite spec
come back synchronously or as a job? What happens when the LLM generates a spec that
references a channel appId that doesn't exist?

This is a load-bearing missing design. The STRUCTURED capability (currently being
designed for PDF auto-annotation) takes a well-defined IO schema as its output
constraint. "Generate a Vega-Lite spec" is a different output shape — it requires its
own prompt engineering, its own schema validation (the Vega-Lite spec is a complex
JSON schema), and its own error handling when the spec fails to render.

**Verdict:** DEFER. Snap dashboards require a dedicated design doc before any
implementation estimate is meaningful. The prerequisite is `shepard-plugin-ai`
STRUCTURED capability (2–3 sprints), and the chart generation layer is an additional
sprint on top of that. This is not a "1 sprint MVP after the plugin ships" — it is
1–2 sprints of prompt engineering and schema validation work after the plugin ships.
The LUMEN TR-004 demo can be told with the side-by-side comparison view (UX P6 /
EP-10) which has a clean, specifiable API and no AI dependency.

---

### 3. CHALLENGE — MfgQuality P4 (Equipment Calibration via AAS Plugin) vs Ontologist P10 (shepard-plugin-calibration)

These two proposals target the same capability — equipment calibration traceability
as a first-class graph concept — and put it in incompatible homes.

**MfgQuality P4** extends `shepard-plugin-aas` with an IDTA "Handover Documentation"
submodel for calibration. Equipment calibration state lives in an AAS Shell Submodel.
The calibration validity check calls into the AAS plugin to read the Submodel.

**Ontologist P10** creates a standalone `shepard-plugin-calibration` with `Equipment`
and `CalibrationRecord` entities, a `USED_CALIBRATED_EQUIPMENT` time-indexed graph
edge, and an SPI hook in core for the validity guard.

The Ontologist's shape is cleaner for three reasons:

1. `CalibrationRecord` is not naturally an AAS submodel — it is a time-indexed
   first-class graph node. Storing it as a Submodel Element in an AAS Shell makes
   the calibration query ("`what was the calibration state of equipment X on date Y`")
   go through an AAS Shell lookup, which is the wrong abstraction layer.

2. The `USED_CALIBRATED_EQUIPMENT {atDate}` graph edge enables a direct Neo4j
   temporal query. The AAS approach requires fetching the entire Submodel and
   parsing it for a validity period — that is an O(submodel-size) read where
   a graph traversal is O(1).

3. If the AAS plugin is not enabled, MfgQuality P4 breaks entirely. The
   Ontologist's standalone plugin degrades gracefully even if AAS is not installed.

**Verdict:** REDIRECT. Use the Ontologist's `shepard-plugin-calibration` shape.
The AAS plugin should be a *downstream consumer* of the calibration plugin — when
both are installed, the AAS Handover Documentation Submodel is generated from
`CalibrationRecord` entities, not vice versa. This is a consistent application
of the "interfaces stay in core; adapters live outside" rule from CLAUDE.md.

---

## API Surface Design Decisions Needed

### D1 — PATCH /v2/data-objects/{appId}/relationships (no agent designed this correctly)

MfgQuality P1 ships `PATCH /v2/data-objects/{appId}/relationships` as a sub-deliverable
of the predecessor-status gate. This is correct — the endpoint is missing and is a
confirmed API gap. But the shape proposed ("`additive only, no removals`") is
dangerously under-specified:

- Does it accept predecessor appIds, or internal node IDs? (Must be appIds.)
- Is it idempotent? (Must be — same payload applied twice should be a no-op.)
- Does it validate that the referenced predecessor appIds exist and the caller has
  Read permission on each? (Must — otherwise a caller can create references to
  DataObjects they cannot see, leaking existence.)
- Does it trigger the predecessor-status gate synchronously? (Yes — the gate check
  happens in the same transaction, not asynchronously.)
- What is the response on a gate violation? (409 Conflict with the blocking-predecessor
  list, as MfgQuality P1 specifies — that shape is correct.)

This endpoint needs a design doc before implementation. It is the most permission-
sensitive new endpoint in the proposal corpus: it mutates the provenance graph, which
is the audit trail. **Recommend:** a separate design doc covering auth, idempotency,
gate behavior, and the wire shape for both the success case and the 409 case. Ship
the endpoint alongside the predecessor-status gate, not before it.

---

### D2 — Bulk Operations Pattern (POST /v2/data-objects/bulk-status vs N sequential calls)

MfgQuality P8 proposes `POST /v2/data-objects/bulk-status` with body
`{ "appIds": [...], "status": "READY" }`. UX P1 proposes N sequential POSTs capped
at 50 with a progress indicator. These are two different API shapes for the same
UX action.

The bulk endpoint shape is correct for the server side — N sequential POSTs is
a sequential-failure-mode antipattern (if call 23 of 50 fails, the state is
half-transitioned with no clean rollback). A single bulk endpoint can apply the
transition gate to all items and return a partial-success response.

But there is no unified batch pattern across `/v2/` today. Before adding a one-off
`/bulk-status` endpoint, decide: is the pattern
`POST /v2/{resource}/batch` with `{ "operations": [{ "appId", "op", "value" }] }`
(generic batch), or is it per-operation bulk endpoints (`/bulk-status`, `/bulk-annotate`)?

**My position:** per-operation bulk endpoints are simpler to version and audit.
A generic batch endpoint is harder to secure (each operation type has different
auth requirements) and harder to document. Ship `POST /v2/data-objects/bulk-status`
as the first instance of the pattern; document it as the pattern other bulk endpoints
will follow. The response shape should be:
```json
{
  "succeeded": ["appId1", "appId2"],
  "failed": [{ "appId": "appId3", "reason": "PREDECESSOR_BLOCKED", "detail": "..." }],
  "total": 15
}
```
Maximum 200 items per batch (as MfgQuality P8 specifies) — that cap belongs in a
constant, not hardcoded.

---

### D3 — SemanticAnnotation Extension Shape (Ontologist P1 + AI P5 + AI P12)

Three proposals extend `SemanticAnnotation` with new fields:

- **Ontologist P1** adds `numericValue` (Double) + `unitIRI` (String) — a
  QuantifiedAnnotation mode.
- **AI P5** agrees with Ontologist P1 and adds a range-query DAO method.
- **AI P12** adds `subtype` (String) + `labelSource` enum to `TimeseriesAnnotation`
  (a separate entity, but related).

The risk: `SemanticAnnotation` is shared with `/shepard/api/` v1. Adding `numericValue`
and `unitIRI` to the wire shape is additive (new nullable fields, existing consumers
ignore them), so v1 compatibility is preserved. But the v2 projection should use a
`SemanticAnnotationV2IO` that makes `numericValue`+`unitIRI` a validated discriminator:
if `numericValue` is set, `unitIRI` must also be set (400 otherwise). The v1 IO
does not enforce this — callers using v1 paths can create invalid numeric annotations
without unit. Design decision needed: enforce the discriminator in both v1 and v2,
or v2-only?

**My position:** Enforce in v2 only. The v1 surface is frozen per CLAUDE.md's
API-version policy. A v2-only validation rule is the correct shape. Document this
explicitly in the migration guide so operators upgrading from upstream know the
validation is v2-specific.

---

### D4 — Ecosystem EP-06 (Controlled-Vocab Enforcement in Core) vs RDM P10 (shepard-plugin-metadata-profiles)

EP-06 puts annotation schema enforcement in core with a `CollectionSchema`
StructuredDataReference entity. RDM P10 creates `shepard-plugin-metadata-profiles`
with a `MetadataProfile` Neo4j entity, admin REST surface, and a status-transition
enforcement hook SPI in core.

The plugin-first rule from CLAUDE.md is clear: features with domain-specific profiles
and their own release cadence belong in a plugin. The enforcement *hook* (the status-
transition gate call) belongs in core as an SPI — this is the "in-tree interfaces +
plugin implementations" pattern from CLAUDE.md §"Always: think plugin-first."

EP-06's framing as a core feature violates the plugin-first principle. The `CollectionSchema`
StructuredDataReference is also the wrong entity type for a machine-enforced schema —
it makes schema definition depend on the structured-data container infrastructure,
which is a layering violation.

**My position:** MERGE into RDM P10's shape. Core ships the `MetadataProfileValidator`
SPI (the hook point in `DataObjectService.updateStatus()`). The plugin ships the
admin surface, the `MetadataProfile` entity, and the evaluation logic. EP-06's
enforcement UI (required-field indicators in DataObject creation form) is a valid
frontend concern that can land independently once the plugin-level profiles are
queryable.

---

### D5 — Grafana Dashboard Export Endpoint Placement

The task brief asks: `GET /v2/timeseries/{containerAppId}/grafana-dashboard` — is
this the right place for it, or should it be a plugin endpoint?

**My position: plugin endpoint.** Grafana is an external tool with its own release
cadence and its own JSON schema (dashboard format changes between Grafana major
versions). The endpoint generates Grafana-version-specific JSON — if Grafana 11
changes its dashboard format, the core API is now coupled to an external tool's
release schedule.

The correct shape: `shepard-plugin-grafana` (new, small plugin) with one endpoint
`GET /v2/grafana/timeseries-dashboard?containerAppId={appId}&grafanaVersion=10`
(version param so the plugin can emit the right schema for the caller's Grafana
install). The plugin has zero core dependency beyond reading from
`TimeseriesContainerService` — which it can do via the existing `/v2/` API or via
a shared service interface.

---

### D6 — JupyterHub D2 Query Params on Collection List

The task brief asks: are `?createdBy=me` and `?watched=true` on
`GET /v2/collections` safe additive extensions?

**Yes, with one design note.** `?createdBy=me` resolves `me` to the authenticated
user at the service layer (not the controller) — the string `"me"` is a well-known
alias, not a username. This is the established pattern from `/v2/admin/users/me`
and `ProfilePane.vue`. `?watched=true` filters to collections the caller has a
`CW1` subscription on — this is a join against the `WATCHES` relationship in Neo4j,
already supported by the subscription service.

Both params fit in the pagination envelope from P5 (same `page`, `size`, `totalCount`
envelope). No schema migration needed. These are safe to add in the same PR as P5's
pagination consistency work.

---

## Merges I'm Calling

### Merge A — Ancestor Chain Endpoint (5 agents, 1 endpoint)

UX P5, API P4, MfgQuality P5, Strategy P3, AI P6, and RDM P11 all specify the same
endpoint with slightly different shapes. The canonical shape:

```
GET /v2/data-objects/{appId}/ancestor-chain
  ?maxDepth=20          (integer 1–50, default 10)
  &direction=predecessors   (predecessors | successors | both)
  &includeActivities=true   (adds PROV-O activities connecting each hop)

→ 200
{
  "root": "appId",
  "depth": 4,
  "truncated": false,
  "chain": [
    { "depth": 0, "dataObject": DataObjectV2IO },
    { "depth": 1, "dataObject": DataObjectV2IO, "via": "HAS_PREDECESSOR", "activity": ActivityIO },
    ...
  ]
}
```

**The one design disagreement:** API P4 adds `{ "redacted": true, "appId": "..." }`
for items the caller cannot read. Strategy P3 and RDM P11 do not. The `redacted`
placeholder is correct and should be in the canonical shape — it tells the caller
the chain continues but the content is access-controlled, which is information the
caller needs (especially for EN 9100 audit: "the chain has 6 hops, but 2 are from
a restricted collection").

**Blocked on:** my P2 (flat-GET) must ship first — each node in the chain is looked
up by appId without knowing the collection.

---

### Merge B — Status Vocabulary + Predecessor Gate (4 agents, 1 design)

MfgQuality P1+P2, Ontologist P6, Strategy P8 all propose the same thing: new status
values (`NCR_OPEN`, `ON_HOLD`, `REWORK`, `REJECTED`, `CERTIFIED`, `SUPERSEDED`) plus
a feature-toggle-gated predecessor gate that returns 409 Conflict when a predecessor
has status in `{NCR_OPEN, ON_HOLD}`.

**Canonical additions:**
- Status values: `NCR_OPEN`, `ON_HOLD`, `REWORK`, `REJECTED`, `CERTIFIED`, `SUPERSEDED`
  (MfgQuality P2's full list — includes `NCR_DISPOSITIONED` which is domain-specific
  but harmless to add)
- Predecessor gate: FeatureToggle `quality.predecessor-gate.enabled`, default OFF
- Immutability lock: CERTIFIED/PUBLISHED → DRAFT/IN_REVIEW transitions require
  `instance-admin` role (Strategy P8 is correct here)
- `PATCH /v2/data-objects/{appId}/relationships` (MfgQuality P1 sub-deliverable) —
  needs its own design doc (see D1 above)

**The one design disagreement:** MfgQuality P2 proposes a `:QualityGateConfig` Neo4j
singleton for the role-gate transition table. Ontologist P6 stores it in the
`FeatureToggleRegistry`. The CLAUDE.md pattern (A3b, N1c2, UH1a) establishes
`:*Config` singletons for runtime-mutable operator knobs. Use `:QualityGateConfig`
as the canonical store — consistent with the established pattern. The
`FeatureToggleRegistry` handles binary on/off toggles; the transition rule table
(which roles can move to which statuses) is richer than a binary flag.

---

### Merge C — Bulk Select + Server-Side Status Filter (3 agents, 1 UX + API unit)

UX P1, MfgQuality P8, Strategy P10 all call for the same two-part change:

1. `?status=` query param on `GET /v2/collections/{appId}/data-objects` (server-side filter)
2. Row selection checkboxes + bulk action toolbar in `CollectionDataObjectsPanel`
3. `POST /v2/data-objects/bulk-status` (see D2 above for shape)

These should land in a single PR: the backend param + the frontend checkboxes +
the bulk endpoint are a coherent unit. Shipping the backend param without the frontend
wastes a release cycle; shipping the checkboxes without the server filter makes the
"select all matching" action wrong (it would only select the visible page).

---

### Merge D — PDF Auto-Annotation Endpoint (3 agents, 1 endpoint)

AI P4, UX P3, API P10 all specify `POST /v2/data-objects/{appId}/suggest-annotations`
with the same shape. The canonical response includes:
- `suggestedAttributes: Map<String, String>`
- `suggestedAnnotations: [{ propertyName, valueName, propertyIRI, valueIRI, confidence }]`
- `aiActivityAppId: string` (provenance trail)

One nuance: AI P4 specifies `→ 503 Retry-After` when plugin-ai is not configured.
UX P3 says the button is hidden when plugin-ai is unavailable. Both are correct for
different callers — the 503 is for API/MCP callers; the hidden button is for UI
callers. Both should be implemented: 503 at the REST layer, hidden button in the
frontend gated on a `GET /v2/admin/plugins` capability check.

**Hard dependency:** `shepard-plugin-ai` STRUCTURED capability (2–3 sprints). The
endpoint can be built against a stub provider today and wired when the plugin ships —
do not wait to build the orchestration.

---

### Merge E — CHAMEO + SSN/SOSA + MFFD Domain Vocabulary (3 agents, 1 manifest PR)

Ontologist P3, MfgQuality P10, Strategy P4 all propose adding CHAMEO and SSN/SOSA
to `ontologies-manifest.json`. Ontologist P5 adds the MFFD/PLUTO domain vocabulary
TTL files. These should land in a single PR:

- `ontologies-manifest.json` gains CHAMEO and SSN/SOSA (SHA-256-pinned, `required: false`)
- `backend/src/main/resources/ontologies/mffd-process.ttl` (MFFD process vocabulary)
- `backend/src/main/resources/ontologies/pluto-mission.ttl` (PLUTO mission phases)
- `backend/src/main/resources/ontologies/lumen-facility.ttl` (facility IRIs)
- QUDT primacy `skos:scopeNote` in the manifest entry

This is a single zero-code-change PR (manifest entries + TTL files). The
`OntologySeedService` handles ingestion. The immediate effect: `AddAnnotationDialog`
gains CHAMEO defect types and SSN/SOSA sensor concepts in the autocomplete. No
migration. No backend service changes.

---

### Merge F — AnnotatableFile Bridge (2 agents, 1 entity + endpoints)

Ontologist P4 and MfgQuality P6 specify the same entity and REST surface. The
canonical endpoint shape:
```
GET  /v2/file-containers/{appId}/annotatable-files
POST /v2/file-containers/{appId}/annotatable-files          ← creates bridge for a specific file
GET  /v2/file-containers/{appId}/annotatable-files/{afId}/annotations
POST /v2/file-containers/{appId}/annotatable-files/{afId}/annotations
```

One design choice not resolved by either agent: should `{afId}` in the path be
the `AnnotatableFile` node's appId, or the `fileObjectId` (MongoDB GridFS OID)?
**Use the AnnotatableFile's appId** — expose GridFS OIDs as little as possible
in the v2 surface (they are storage-implementation details; the AnnotatableFile
node is the stable identifier).

---

### Merge G — Material Batch Pattern (2 agents, 1 design + 1 seed + 1 endpoint)

Ontologist P2 and MfgQuality P3 agree: material batches are DataObjects with a
convention, not a new entity type. The `GET /v2/collections/{appId}/lot-lineage?lot_id={id}`
endpoint (Ontologist P2) is additive and useful. MfgQuality P3's `shepard-template-materialbatch.yaml`
is the operator-friendly on-ramp.

Both proposals agree the seed update (`examples/lumen-showcase/seed.py`) should
create MaterialBatch DataObjects with Predecessor links for LOX and LCH4 lots.
This should land in the same PR as the lot-lineage endpoint.

---

### Merge H — Anomaly → Notification Bridge (2 agents, 1 integration)

AI P2 and MfgQuality P9 specify the same NTF1 bridge. The divergence: MfgQuality P9
adds NCR auto-creation (feature-toggle gated, default OFF); AI P2 stops at the
notification. Both shapes are correct. Land them in one PR with the auto-creation
behind the toggle. Hard dependency: NTF1 must ship first. Mark this as blocked
until NTF1 lands.

---

## My Overall Priority Stack

### Priority ordering principle

Fixes live bugs → unblocks other features → closes HMC-deadline items →
advances manufacturing use case → advances FAIR/publication stack →
UI polish and discovery features.

---

### Tier 1 — No blockers, unblock everything else (land these first)

| Item | Effort | What it unblocks |
|---|---|---|
| **API P1** — Typed container arrays in DataObjectV2IO | XS–S | MCP server (live bug), all agent traversal chains, manifest generator |
| **API P2** — Flat `GET /v2/data-objects/{appId}` | S | Ancestor chain endpoint, predecessor gate, provenance walk |
| **API P7** — ProblemJson ExceptionMapper globally | S | All SDK consumers, MCP error parsing, plugin error shapes |
| **API P8** — Human-readable OpenAPI tags | XS | SDK class names, MCP tool naming, generated SDK usability |
| **API P5 (Part A)** — `totalCount`+`hasMore` on all list responses | S | Every paginated UI, batch operations, curator workflows |
| **API P5 (Part B)** — `?status=` server-side filter on DataObject list | S | Curator filter bug, bulk-select UX, NCR status filtering |
| **Merge E** — CHAMEO + SSN/SOSA + domain vocabulary TTLs | S | Annotation picker gains defect vocabulary; all MFFD annotation proposals |

---

### Tier 2 — Parallel after Tier 1 clears

| Item | Effort | Notes |
|---|---|---|
| **Merge A** — Ancestor chain endpoint | M | Blocked on P2; 5-agent convergence; EN 9100 critical path |
| **Unified FAIR Metadata Spine (Merge from 8 agents)** | M | HMC deadline 06 July 2026 — highest time-critical item in corpus |
| **API P9** — TS-IDa + TS-IDb (channel appId) | M | ML pipeline stability; live-window table scan fix |
| **API P3** — POST /v2/import/jobs execute | M | Agentic ingest; MFFD pipeline; dead-end repair |
| **PATCH /v2/data-objects/{appId}/relationships** (MQ P1 sub-deliverable) | S–M | Needs design doc D1 first |
| **Merge G** — Material Batch pattern + lot-lineage endpoint + seed update | S | EN 9100 §8.5.2; LUMEN seed upgrade |

---

### Tier 3 — After Tier 2 (parallel tracks)

| Track | Items | Notes |
|---|---|---|
| **Manufacturing quality** | Merge B (status vocab + gate), Merge C (bulk select + status filter), Merge F (AnnotatableFile) | EN 9100 critical path; gate depends on status vocab |
| **Data model** | Ontologist P1 (QuantifiedAnnotation), Ontologist P9 (Label Refresher), Ontologist P12 (CausalEdge) | Model extensions; QuantifiedAnnotation needs design doc D3 |
| **AI surface** | Merge D (PDF auto-annotation endpoint, stub provider), AI P1 (channel quality badge, FE only) | Auto-annotation needs plugin-ai; quality badge has zero backend deps |
| **FAIR/publication** | API P6 (metadata-completeness endpoint), RDM P7 (tombstone), RDM P8 (qualified PID references) | Tombstone and qualified PID are additive; completeness score depends on FAIR fields |

---

### Tier 4 — After plugin-ai foundation ships

| Item | Effort | Notes |
|---|---|---|
| Merge D wired (PDF auto-annotation, live inference) | S add-on | Connect stub to real provider |
| Merge H (Anomaly → NTF1 bridge) | S | Needs NTF1 to ship |
| RDM P6 (shepard-plugin-publisher, Zenodo) | L | Needs FAIR fields + license field |
| Ontologist P11 (AAS install.md + semantic mapping extension) | M | Plugin improvement; AAS team bandwidth |
| Analytics AI P7 (Audit narrative generator) | M | Needs plugin-ai TEXT + ancestor chain |
| Analytics AI P8 (semantic embedding) | L | Needs plugin-ai EMBEDDING; trigger: ~200 DataObjects |

---

### Deferred (needs design doc before scoping)

| Item | Reason |
|---|---|
| Strategy P7 (Snap Dashboards MVP) | No API shape; design doc needed (D-snap) |
| EP-03 (Public landing page) | Security design doc needed before implementation |
| D1 — PATCH /v2/relationships | Auth + idempotency design doc needed |
| D4 — Metadata profiles plugin | Merge decision (EP-06 vs RDM P10) must be documented |
| Grafana dashboard export | Redirect to `shepard-plugin-grafana` (D5) |
| RDM P10 / EP-06 (Metadata profile enforcement) | Plugin-first architectural decision first |

---

## The 1 Endpoint That Needs a Design Doc Before Anyone Touches It

**`PATCH /v2/data-objects/{appId}/relationships`**

This endpoint mutates the provenance graph — the audit trail — in a way that no
other current endpoint does. It adds or removes predecessor/successor links, which
means it changes the ancestor-chain traversal that EN 9100 auditors rely on. It must
get the following right before a single line of implementation is written:

1. **Auth model:** Caller must have Write on both the subject DataObject and Read on
   every referenced predecessor. This is a cross-collection permission check with no
   current precedent in the codebase.

2. **Idempotency:** Posting the same predecessor set twice must be a no-op. Partial
   updates (add this appId, remove that appId) must be atomic.

3. **Gate interaction:** The predecessor-status gate must fire synchronously in the
   same transaction. If adding predecessor P makes the subject DataObject blocked
   (because P has status NCR_OPEN), the PATCH must 409 immediately — not accept
   the update and then block unrelated operations later.

4. **Provenance:** The PATCH itself must generate a PROV-O activity
   (who changed the predecessor link, when, from what to what). This is the record
   that tells an auditor "the lineage was modified after creation."

5. **Removal semantics:** Is predecessor removal allowed? MfgQuality P1 says
   "additive only." But what if a predecessor was added by mistake? Define the
   policy before shipping.

Ship this endpoint only after a design doc resolves all five points. It is fine
to ship the predecessor-status gate check inside `DataObjectService.createDataObject`
(which is already a single-endpoint operation with cleaner auth semantics) while the
PATCH design doc is in progress.

---

*API Scrutinizer | Generated 2026-05-21*
