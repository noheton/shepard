---
stage: decommissioned
last-stage-change: 2026-05-23
---

# API Scrutinizer — Concrete Improvement Proposals

**Author:** API Scrutinizer agent  
**Date:** 2026-05-21  
**Scope:** Synthesised from all 6 peer findings + plugin REST audit + feature matrix review  
**Stance:** Each proposal is the smallest API surface change that closes a real caller pain — no gold-plating.

---

## Proposal 1: Typed Container Reference Arrays in DataObjectV2IO

**Problem it solves**

The single worst live bug in the current API: `DataObjectIO.referenceIds` emits a flat `long[]` of `BasicReference` node IDs. Any caller (MCP server, AI agent, importer) that reads `referenceIds` and passes those values to `GET /v2/…` endpoints gets a 404, because those IDs are internal Neo4j edges, not DataObject or container appIds. This caused live 404s in the MCP server (api-scrutinizer §"referenceIds Problem"). The analytics agent's LLM manifest generator (analytics-ai §5 Opportunity 5) and the UX auditor's "trace upstream" path (ux-auditor Idea A) both need unambiguous container pointers to work.

**What it looks like**

Introduce `DataObjectV2IO` as a subtype of `DataObjectIO` (the same pattern `DataObjectListItemV2IO` already uses for count fields). The `/v2/` single-object and list responses use this subtype.

```
GET /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}
→ 200 DataObjectV2IO

DataObjectV2IO extends DataObjectIO:
  // Remove from v2 response (keep in v1 via the base class):
  referenceIds: long[]          ← REMOVED from v2 projection

  // Add to v2 response (appId-addressed, directly actionable):
  timeseriesReferenceAppIds: string[]   // each is a valid /v2/timeseries-references/{appId} key
  fileReferenceAppIds:        string[]   // each is a valid /v2/file-references/{appId} key
  structuredDataReferenceAppIds: string[] // each is a valid /v2/structured-data-references/{appId} key
  gitReferenceAppIds:         string[]   // plugin-aas / git plugin references
  videoStreamReferenceAppIds: string[]   // video plugin references
  hdfContainerAppIds:         string[]   // hdf5 plugin references
```

The v1 surface stays byte-identical — `referenceIds` in v1 is untouched.

**Plugin or core?** Core (DataObjectIO is a core class; the projection is a backend-only change).

**Effort:** S (2–3 days; the pattern is already established in `DataObjectListItemV2IO`).

**Domain impact:** All domains. MFFD scripts that chain from DataObject → timeseries channels are blocked on this today. MCP server is blocked on this today. AI manifest generator is blocked on this today.

**Cross-finding hook:** Unlocks analytics-ai Opportunity 5 (manifest generator), ux-auditor Idea A (trace upstream, needs container pointers), research-data-manager §3 (RO-Crate with qualified references needs stable appIds on each container).

---

## Proposal 2: GET /v2/data-objects/{appId} — Flat DataObject Lookup

**Problem it solves**

There is no flat endpoint to fetch a single DataObject by its appId without knowing the Collection it belongs to. A caller arriving from a provenance trail, a notification body, a template instantiation response, or a KIP PID resolution only has the DataObject appId. They must call `GET /v2/collections` → paginate → find the one collection → then `GET /v2/collections/{cA}/data-objects/{dA}`. At MFFD scale (N collections, 300+ DataObjects each) that is many round-trips (api-scrutinizer §"Missing Operations"). The UX auditor's "Trace provenance upstream" idea hits this wall on every predecessor hop (ux-auditor §"Gaps & Blockers G5"). The manufacturing quality audit scenario — trace TR-004's defect back to the propellant batch DataObject — requires hopping through a predecessor chain whose members may span multiple collections.

**What it looks like**

```
GET /v2/data-objects/{appId}
→ 200 DataObjectV2IO (same shape as Proposal 1)
→ 404 if the caller has no Read permission on the DataObject's collection (same auth as the nested path)

// No collection context needed. EntityIdResolver translates appId → Neo4j id,
// then PermissionsService checks the containing collection's permissions.
```

The nested path `GET /v2/collections/{cA}/data-objects/{dA}` stays. This is an additive alternative for callers who already have the collection appId and want to keep the URL semantically scoped.

**Plugin or core?** Core.

**Effort:** S (1–2 days; EntityIdResolver already handles the appId lookup; the collection permission check is already a DAO call).

**Domain impact:** All domains. Critical for AI agents (MCP server, manifest generator). Critical for the provenance-chain traversal use case (auditor, IME).

**Cross-finding hook:** Unlocks ux-auditor Idea A (ancestor-walk endpoint — each hop needs a flat lookup), manufacturing-quality §5 Opportunity 7 (predecessor-status gate — the gate must locate predecessors without knowing their collections), analytics-ai §8 "Anomaly digest on collection" (DataObject pointers arrive from TimeseriesAnnotation nodes, not from collection context).

---

## Proposal 3: POST /v2/import/jobs — Execute a Validated Import

**Problem it solves**

`POST /v2/import/validate` produces a `commitId` that expires in 24 hours. `POST /v2/import/jobs` is referenced in Javadoc and OpenAPI descriptions of three existing endpoints but does not exist. The validate endpoint is the most elaborate dead end in the API (api-scrutinizer §"Missing Operations, CRITICAL"). The analytics agent's LLM manifest-generator (analytics-ai §5 Opportunity 5) calls context → generates manifest → validates → needs to execute. Without the execute leg, agentic import is a demo that ends at the planning stage. The research data manager's `shepard-plugin-publisher` async flow (rdm §5) follows the same 202/poll pattern and is the correct blueprint.

**What it looks like**

```
POST /v2/import/jobs
Content-Type: application/json
{
  "commitId": "abc123",          // from POST /v2/import/validate response
  "dryRun": false,               // optional, default false
  "agentContext": {              // optional, from AgentContextIO — carries aiActivityAppId if LLM generated
    "generatedByAiActivityAppId": "01924b5c-..."
  }
}

→ 202 Accepted
{
  "jobAppId": "01924b5c-...",
  "status": "QUEUED",
  "commitId": "abc123",
  "estimatedDataObjects": 15
}

GET /v2/import/jobs/{jobAppId}
→ 200
{
  "jobAppId": "...",
  "status": "RUNNING" | "COMPLETED" | "FAILED",
  "progress": { "created": 12, "total": 15 },
  "errors": [],
  "completedAt": null
}
```

The import execution is async (worker thread or Quarkus @Asynchronous). On completion, emit a NTF1 notification to the submitting user.

**Plugin or core?** Core (import is a core primitive; plugin manifests declare payload kinds but the executor is the core import service).

**Effort:** M (1 week; the manifest model and validation logic already exist; the execution path through the existing DAO layer needs wiring and a job-state Neo4j or Postgres entity).

**Domain impact:** All domains. MFFD ingest pipeline, LUMEN seed refresh, AI agent import flow all gate on this.

**Cross-finding hook:** Unlocks analytics-ai Opportunity 5 (full agentic ingest loop), strategy-advisor Recommendation 1 (real MFFD dataset ingestion), ux-auditor §"Gaps" (importer plugin design seed from project memory).

---

## Proposal 4: GET /v2/data-objects/{appId}/ancestor-chain — Recursive Predecessor Walk

**Problem it solves**

The UX auditor identified the 6-predecessor truncation (`slice(0, 6)` in `DataObjectProvGraph.vue`) as a CRITICAL blocker for DIN EN 9100 compliance audit traces (ux-auditor §"Persona 3 Compliance Auditor"). The manufacturing quality agent confirmed: "an auditor tracing a 4-hop defect chain must manually navigate DataObject by DataObject" with no recursive server-side walk (mfg-quality §2). The client-side alternative (N sequential `GET /v2/data-objects/{appId}` calls per hop) is feasible but requires Proposal 2 first and is O(depth) in round-trips with no depth control. A server-side walk is a single Cypher query with depth binding.

**What it looks like**

```
GET /v2/data-objects/{appId}/ancestor-chain?maxDepth=20&direction=predecessors

→ 200
{
  "root": "01924b5c-...",
  "depth": 4,
  "truncated": false,   // true if maxDepth reached before exhaustion
  "chain": [
    {
      "depth": 0,
      "dataObject": { /* DataObjectV2IO — full shape */ }
    },
    {
      "depth": 1,
      "dataObject": { /* predecessor */ },
      "via": "PREDECESSOR_OF"
    },
    ...
  ]
}

// direction: "predecessors" (default) | "successors" | "both"
// maxDepth: integer, 1–50, default 10
// Auth: caller must have Read on every DataObject's collection;
//       items the caller cannot read are replaced with
//       {"depth": N, "redacted": true, "appId": "..."}
//       so the chain depth is visible even if content is restricted.
```

The Cypher is a bounded variable-length path: `MATCH (start)-[:HAS_PREDECESSOR*1..{maxDepth}]->(anc) WHERE start.appId = $appId RETURN anc, length(path)`.

**Plugin or core?** Core.

**Effort:** M (3–4 days; the Cypher is straightforward; the auth check per node is the fiddly part — use the existing `PermissionsService.isAllowed` per item).

**Domain impact:** MFFD manufacturing (DIN EN 9100 defect trace), PLUTO (TC→TM causal chain traversal noted in data-ontologist §3.2), AI agents (provenance gap detection in analytics-ai §8 Opportunity 4).

**Cross-finding hook:** Directly closes ux-auditor §"Idea A — Ancestor walk", manufacturing-quality §7 "Process chain position indicator", data-ontologist §3.2 PLUTO gap ("which command caused which response" at DataObject granularity).

---

## Proposal 5: Unified Pagination Envelope + Status-Filter on DataObject List

**Problem it solves**

Two pagination shapes coexist in `/v2/`: `?page=?size` (majority) and `?limit` (provenance). No response envelope includes `totalCount` or `hasMore`. Callers cannot know when they've exhausted a list without comparing the count of returned items to the `size` parameter (api-scrutinizer §"Pagination Consistency"). The UX auditor found that the status filter on the DataObjects table is client-side only: it silently under-reports because it filters only the current page (ux-auditor §"Persona 2 Data Curator, Risk 4"). A data curator filtering for all IN_REVIEW DataObjects across a 500-item collection sees only the 2–3 that happen to be on the visible page.

**What it looks like**

**Part A — Add `totalCount` to all list responses:**

```
// Every GET /v2/*/... list response gains:
{
  "items": [...],
  "page": 0,
  "size": 50,
  "totalCount": 342,
  "hasMore": true
}

// For ProvenanceRest, migrate ?limit → ?page+?size and wrap in this envelope.
// Keep ?limit as a deprecated alias for one release cycle.
```

**Part B — Add server-side status filter to DataObject list:**

```
GET /v2/collections/{collectionAppId}/data-objects
  ?page=0&size=50
  &status=IN_REVIEW           // NEW: server-side filter, passed to DAO WHERE clause
  &name=AFP                   // existing, unchanged
  &sortBy=createdAt&order=desc // existing
```

The DAO already has a name filter; adding a status filter is a second `AND n.status = $status` predicate in the Cypher.

**Plugin or core?** Core.

**Effort:** S (2–3 days for totalCount on all list endpoints; 1 day for the DataObject status filter).

**Domain impact:** General researcher, data curator. MFFD quality workflow (find all NCR_OPEN DataObjects in a collection). Analytics agent batch operations.

**Cross-finding hook:** Closes ux-auditor §"Risk 4 — Client-side status filter pagination gap" (curator can now filter across full collection server-side). Enables manufacturing-quality §5 Opportunity 5 (new status values like NCR_OPEN are only useful if the curator can filter on them). Enables research-data-manager §4 (metadata completeness score — need to enumerate all DataObjects without status-filter gap).

---

## Proposal 6: GET /v2/collections/{appId}/metadata-completeness — FAIR Score Endpoint

**Problem it solves**

The research data manager found that the Unhide feed asserts `schema:license` from a field that does not exist on `Collection.java` (rdm §1). KIP PID minting has no completeness gate. A researcher can mint a DOI for a Collection with no license, no ORCID, no funder reference, and no access rights — in violation of Horizon Europe Art. 17 and DFG research data requirements (rdm §9 funding mandate table). This is a single sprint to close the gap that blocks EU Horizon compliance. The strategy advisor flagged the license + ORCID gaps as the fastest-ROI FAIR changes (strategy-advisor §8 Rec 3).

**What it looks like**

```
// Backend: MetadataCompletenessService (new, ~150 LOC)

GET /v2/collections/{appId}/metadata-completeness
→ 200
{
  "score": 72,
  "level": "GOOD",              // POOR <50 / FAIR 50–79 / GOOD 80–100
  "checks": [
    {"id": "name",          "passed": true,  "label": "Name present",               "points": 5},
    {"id": "description",   "passed": true,  "label": "Description ≥ 50 chars",     "points": 10},
    {"id": "annotation",    "passed": true,  "label": "At least one annotation",    "points": 10},
    {"id": "license",       "passed": false, "label": "Usage license (SPDX ID)",    "points": 20},
    {"id": "accessRights",  "passed": false, "label": "Access rights set",          "points": 10},
    {"id": "orcid",         "passed": true,  "label": "Creator ORCID stamped",      "points": 15},
    {"id": "funder",        "passed": false, "label": "Funder reference present",   "points": 15},
    {"id": "pid",           "passed": true,  "label": "PID minted (KIP)",           "points": 10},
    {"id": "embargo",       "passed": null,  "label": "Embargo date (if embargoed)","points": 5}
  ],
  "minimumForPublish": 60,
  "minimumForHkgFeed": 80
}

// Also: gate PublishService.publish() at score >= minimumForPublish (configurable; default 60).
// Admin can override: POST /v2/publish?force=true (instance-admin only).

// Prerequisite fields that must be added to AbstractDataObject (additive, no migration):
//   license: String (SPDX identifier, nullable)
//   accessRights: enum OPEN|EMBARGOED|RESTRICTED (nullable)
//   embargoEndDate: String ISO-8601 (nullable)
//   createdByOrcid: String (stamped from User.orcid at creation time)
//   fundingReferences: List<String> (nullable, freetext grant IDs)
```

**Plugin or core?** Core (FAIR compliance is a core concern; the publisher plugin (shepard-plugin-publisher) consumes this score, not produces it).

**Effort:** M (1 week: 3 days for the new fields on AbstractDataObject + IO classes + migration; 2 days for the score service + endpoint; frontend widget is a separate sprint).

**Domain impact:** MFFD (Clean Aviation JU Horizon Europe mandate), PLUTO (DFG mandate), general researcher (any Helmholtz-funded project).

**Cross-finding hook:** Directly closes research-data-manager §4 (the score spec was designed there). Enables strategy-advisor Recommendation 3 (HMC Project Call 2026 submission requires demonstrable FAIR compliance). Enables the Unhide feed's `schema:license` assertion to have an actual source field.

---

## Proposal 7: Unified Error Shape — ProblemJson Everywhere

**Problem it solves**

Four distinct error shapes are in active use across `/v2/`: RFC 7807 ProblemJson (minority), `ApiError` JSON (minority), plain string (majority), and hand-rolled JSON strings (two endpoints). The majority of the surface returns plain strings — callers parsing the response as JSON get a parse error precisely when something goes wrong (api-scrutinizer §"Error Shape Consistency"). Any generated SDK (codegen tools emit per-status response types) generates four different error types for what should be one. Plugin REST surfaces (aas, video, git — confirmed by code review) inherit this inconsistency because there is no base class enforcing the contract.

**What it looks like**

```java
// New: global JAX-RS ExceptionMapper, registered in core

@Provider
public class DefaultProblemJsonExceptionMapper implements ExceptionMapper<Exception> {
    @Override
    public Response toResponse(Exception e) {
        if (e instanceof WebApplicationException wae) {
            return Response.status(wae.getResponse().getStatus())
                .type("application/problem+json")
                .entity(ProblemJson.of(wae))
                .build();
        }
        // 5xx: return generic ProblemJson with traceId only; no internal detail
        return Response.status(500)
            .type("application/problem+json")
            .entity(ProblemJson.generic(traceId(e)))
            .build();
    }
}
```

All hand-rolled string returns in `AnomalyDetectionRest`, `ImportV2Rest`, `CollectionSnapshotRest`, `TimeseriesAnnotationRest`, `TemplateInstantiationRest`, `FileBundleReferenceRest`, `TimeseriesLiveWindowRest`, `AdminUserOrcidRest`, `AdminUserGitCredentialRest` become dead code — they will never be reached once the mapper handles the exception at the framework level.

Plugin resources (aas, video, git) automatically benefit because the mapper is registered in the core CDI context, not per-plugin.

**Plugin or core?** Core (the mapper is registered once; plugins inherit it).

**Effort:** S (2 days: register the mapper, update the 9 resources that hand-roll errors to throw `WebApplicationException` instead, add one contract test per major resource group).

**Domain impact:** All callers and all generated SDKs. AI agent callers (MCP server) benefit most — error responses are currently unparseable JSON, which breaks structured error handling in the agent.

**Cross-finding hook:** Plugin REST audit found that git, video, and aas plugins all lack a uniform error shape. This fixes all plugins at once with zero plugin-side changes.

---

## Proposal 8: Human-Readable OpenAPI Tags (Replace Internal Task Codes)

**Problem it solves**

Fifteen-plus REST classes carry `@Tag` values that embed internal design-doc task codes: `TS_LIVE1`, `CC1b`, `CC2`, `WATCH1`, `IMP1`, `SA-CONT`, `CW1`, `TS_CHART_VIEW1`, `TS_STATS1`, `FS1c`, `FS1g`. These appear verbatim in the generated OpenAPI spec under the `tags` grouping. Any generated SDK emits `TagTsLive1Api`, `TagImp1Api` as class names (api-scrutinizer §"OpenAPI tag pollution"). The strategy advisor noted that the OpenAPI spec is the developer-facing API surface for external evaluation (strategy-advisor §2); code-gen names like `TagCc1bApi` are barriers to adoption (and the `aidocs/44` feature matrix notes external SDK generation is in scope).

**What it looks like**

```java
// Before:
@Tag(name = "TS_LIVE1")

// After:
@Tag(name = "Timeseries — Live Window")

// Full rename map:
TS_LIVE1       → "Timeseries — Live Window"
CC1b           → "Collections"
CC2            → "Collections"
WATCH1         → "Container Pinning"
CW1            → "Collection Subscriptions"
IMP1           → "Import"
SA-CONT        → "Container Annotations"
TS_CHART_VIEW1 → "Timeseries — Chart View"
TS_STATS1      → "Timeseries — Stats"
FS1c           → "File Storage"
FS1g           → "File Bundles"
```

Also: rename `GET /v2/collections/{appId}/watches` → `GET /v2/collections/{appId}/subscribers` (the user-watches-Collection concept) to distinguish it from `GET /v2/collections/{appId}/watched-containers` (the Collection-pins-Container concept). These two names are currently one URL segment apart and represent orthogonal concepts, causing persistent caller confusion.

**Plugin or core?** Core (the tags are in core REST classes; plugin tags are already cleaner — e.g., `"AAS"`, `"Git references (v2)"` — and don't need changes).

**Effort:** XS (1 day: mechanical rename; the aliases `CC1b` and `CC2` both map to `"Collections"` which deduplicates them in the spec automatically; rename the `watches` endpoint path in a separate commit as a non-breaking addition with the old path deprecated via `@Sunset`).

**Domain impact:** SDK consumers, AI agent tool-calling (MCP server generates tool names from OpenAPI tags; human-readable names make tool invocation more reliable for the LLM).

**Cross-finding hook:** analytics-ai §3 "Plugin-AI Capability Definition" — the MCP server tools are derived from OpenAPI; legible tag names directly improve LLM tool-selection accuracy.

---

## Proposal 9: Timeseries Channel AppId (TS-IDa + TS-IDb) — Prioritise as ML Infrastructure

**Problem it solves**

The analytics agent found that the 5-tuple is not merely a developer ergonomics problem — it is an ML data integrity problem (analytics-ai §1 "5-tuple ML pipeline tax"). Every training-data export script, every evaluation pipeline, every generated manifest must embed `{measurement, device, location, symbolicName, field}` or invent its own channel key. A channel rename silently breaks all existing scripts. The design doc `aidocs/87` already exists and the migration script is written. TS-IDa (mint UUIDs on existing `Timeseries` nodes) and TS-IDb (expose `appId` in channel list/get responses) are zero-risk additive changes. The live-window endpoint currently loads ALL channels into JVM memory and filters in Java — a full table scan per live-mode poll (api-scrutinizer §"What Surprised Me"). TS-IDb converts that to a single index lookup.

**What it looks like**

Per `aidocs/87`, two phases:

**TS-IDa** — Neo4j migration: `MATCH (t:Timeseries) WHERE t.appId IS NULL SET t.appId = randomUUID()`. Idempotent. No API change.

**TS-IDb** — Expose in responses:

```
GET /shepard/api/timeseriesContainers/{id}/timeseries
→ each item gains: "appId": "01924b5c-..."  (additive, backward-compatible)

GET /v2/timeseries-containers/{containerAppId}/channels/live-window
  → accept ?timeseriesAppId=01924b5c-...  (NEW, replaces 5-tuple filter for v2 callers)

POST /v2/import (manifest)
  → TimeseriesChannelRef gains: "timeseriesAppId": "01924b5c-..."  (alternative to 5-tuple)

TimeseriesContainerChartViewIO.selectedChannels[]
  → migrate from pipe-separated 5-tuple strings to appId array (Neo4j data migration)
```

**Plugin or core?** Core.

**Effort:** M (1 week: 2 days for the Neo4j migration + TS-IDa; 3 days for TS-IDb endpoint additions; 1 day for `selectedChannels` format migration).

**Domain impact:** AI agents/MCP (CRITICAL — every ML pipeline is blocked on stable channel IDs), MFFD manufacturing (AFP sensor channel addressing), PLUTO (telemetry channel addressing).

**Cross-finding hook:** analytics-ai §1 ("TS-IDa and TS-IDb are zero-risk additive changes that should ship before any ML pipeline is built on top"), analytics-ai §8 "Channel-level embedding for cross-run similarity" (requires stable appId to store embedding vector per channel), api-scrutinizer §3 "Top 3 changes for Developer Experience #2".

---

## Proposal 10: POST /v2/data-objects/{appId}/suggest-annotations — PDF Auto-Annotation

**Problem it solves**

The analytics agent specified this as the highest-impact near-term AI feature (analytics-ai §3 Quick Win). The data ontologist found that the semantic annotation infrastructure is fully in place (QUDT, PROV-O, metadata4ing, SiMaT all seeded) but the LUMEN seed never uses it for operational attributes — `test_engineer`, `propellant`, `bench` remain freetext strings (data-ontologist §1.1). The UX auditor found that adding a single annotation requires 7 clicks × 50 channels = 350 interactions for a post-recording batch annotation session (ux-auditor §"Persona 2 Data Curator"). The AI auto-annotation path collapses that to: upload report → review suggestions → accept/reject → done.

**What it looks like**

```
POST /v2/data-objects/{dataObjectAppId}/suggest-annotations
Content-Type: application/json
{
  "fileReferenceAppId": "01924b5c-..."  // the uploaded PDF/Markdown report
}

→ 200
{
  "suggestedAttributes": {
    "propellant": "LOX/LCH4",
    "bench": "P3-Lampoldshausen",
    "test_date": "2026-03-14"
  },
  "suggestedAnnotations": [
    {
      "propertyName": "Experiment Phase",
      "valueName": "Hot-fire test",
      "propertyIRI": "https://...",
      "valueIRI": "https://...",
      "confidence": 0.92
    }
  ],
  "aiActivityAppId": "01924b5c-..."  // provenance node already written, aiGenerated=true
}

→ 503 (with Retry-After) if shepard-plugin-ai STRUCTURED capability is not configured
→ 422 if fileReferenceAppId does not resolve to a text-extractable file type
```

The endpoint is a pass-through orchestrator: it fetches the file bytes from the FileReference, calls the plugin-ai STRUCTURED capability with the existing ontology term list from `GET /v2/import/context`, and returns suggestions. Nothing is written to the DataObject until the caller makes a subsequent `PATCH /v2/collections/{cA}/data-objects/{dA}` (attributes) and `POST /v2/data-objects/{dA}/annotations` (annotations) calls. The `aiActivityAppId` in the response allows provenance tracking of which AI call produced which suggestion, even if the user modifies them before applying.

**Plugin or core?** Core endpoint (the orchestration logic belongs in core because it touches DataObjects, FileReferences, and ontology terms — all core entities). The AI inference is delegated to `shepard-plugin-ai` (STRUCTURED capability slot). Degrades gracefully if the plugin is absent.

**Effort:** M (1 week core endpoint; requires `shepard-plugin-ai` foundation sprint — 2–3 sprints — before the inference call can be made; the orchestration can be coded and tested with a stub provider in parallel).

**Domain impact:** MFFD manufacturing (AFP test report → auto-annotation at ingest time), PLUTO (mission report parsing), general researcher (any domain with structured report uploads).

**Cross-finding hook:** analytics-ai §3 (full quick-win spec), data-ontologist §6 Opportunity 1 + §7 Idea B (unit annotation as mandatory field, annotation suggestion in AddAnnotationDialog), ux-auditor §"Idea C" (annotation suggestion from channel name — a related UX surface).

---

## Proposal 11: GET /v2/collections/{appId}/anomaly-digest — Cross-DataObject QC Summary

**Problem it solves**

The analytics agent proposed this as a zero-LLM, pure-graph query (analytics-ai §8 "Anomaly digest on collection"). The manufacturing quality agent found that the quality score field (`qualityScore` on `TimeseriesReference`) is never surfaced in the UI — it exists only in the backend (mfg-quality §2, "MINOR gap: quality score exists in backend but is invisible to users"). Project managers and process engineers monitoring a campaign (15 LUMEN runs, 300+ MFFD DataObjects) have no way to get a QC status overview without opening individual DataObjects and navigating to the AI anomaly detection panel.

**What it looks like**

```
GET /v2/collections/{collectionAppId}/anomaly-digest
  ?minConfidence=0.5     // only anomalies above this threshold
  &since=2026-05-01T00:00:00Z  // optional time window
  &page=0&size=50

→ 200
{
  "totalAnomalies": 12,
  "affectedDataObjects": 3,
  "items": [
    {
      "dataObjectAppId": "...",
      "dataObjectName": "TR-004",
      "timeseriesReferenceAppId": "...",
      "channel": {
        "symbolicName": "vib_fuel_pump_x",
        "field": "value",
        "appId": "..."   // once TS-IDb ships
      },
      "anomalyAnnotationAppId": "...",
      "startNs": 1234567890,
      "endNs": 1234567999,
      "confidence": 0.92,
      "peakValue": 12.3,
      "maxZScore": 11.5,
      "aiGenerated": true,
      "detectedAt": "2026-05-20T14:33:00Z"
    }
  ],
  "page": 0,
  "size": 50,
  "totalCount": 12,
  "hasMore": false
}
```

Implementation: Cypher query over `TimeseriesAnnotation` nodes filtered by `label = 'anomaly'` and `aiGenerated = true`, joined through `TimeseriesReference → DataObject → Collection` edges. No model change — only a new query.

**Plugin or core?** Core (uses existing AI1b output; no plugin dependency).

**Effort:** S (2–3 days: Cypher query + REST endpoint + pagination wrapper. Frontend widget is a separate sprint).

**Domain impact:** MFFD manufacturing (process QC overview per campaign), AI agents/MCP (QC summary without per-DataObject navigation), general researcher.

**Cross-finding hook:** analytics-ai §8 "Anomaly digest on collection", mfg-quality §2 "AI1c Channel quality scoring" (the digest is the QC surface the quality score needs), ux-auditor §"Idea B — Pinnable live channel tiles on personal digest" (the digest is the data source for the "recent anomalies" tile).

---

## Proposal 12: Plugin REST Surface — Consistent Pagination and Error Contracts

**Problem it solves**

The plugin REST audit found that plugins implement their own pagination and error shapes independently:

- `aas/AasShellsRest.java`: uses `?page` + `?size` (correct for most), but returns raw `AasShellListIO` with no `totalCount` field.
- `git/GitReferenceRest.java`: uses `?page` + `?size` (correct) with `GitReferenceListIO`, no `totalCount`.
- `video/VideoStreamReferenceV2Rest.java`: same pattern, no `totalCount`.
- `video/VideoAnnotationRest.java`: annotations are returned as a flat list with no pagination at all (could grow unbounded on a long-duration video).
- All plugins: error shapes are inconsistent — some throw `WebApplicationException` (gets Proposal 7's mapper), some return `Response.status(...).entity("plain string").build()` directly, bypassing the mapper.

This is the plugin-API-surface version of the same problem seen in core (api-scrutinizer §"Error Shape Consistency").

**What it looks like**

A shared plugin SDK module (`shepard-plugin-sdk`, already planned as part of `aidocs/platform/47-dev-experience-and-plugin-system.md`) should expose:

```java
// In shepard-plugin-sdk (new or extend existing):

// 1. PagedResponse<T> wrapper — every plugin list endpoint returns this
public record PagedResponse<T>(
    List<T> items,
    int page,
    int size,
    long totalCount,
    boolean hasMore
) {}

// 2. Shared ExceptionMapper — already in core via Proposal 7;
//    plugins must NOT register their own ExceptionMappers.
//    Plugin errors should throw WebApplicationException or
//    the ShepardPluginException class (new, extends WebApplicationException).

public class ShepardPluginException extends WebApplicationException {
    public ShepardPluginException(String detail, Response.Status status) { ... }
}
```

Concrete plugin fixes:
- `VideoAnnotationRest`: add `?page` + `?size` parameters; wrap return in `PagedResponse<VideoAnnotationIO>`.
- `AasShellsRest`, `GitReferenceRest`, `VideoStreamReferenceV2Rest`: add `totalCount` + `hasMore` to their existing list response shapes.
- All plugins: replace `Response.status(400).entity("plain string").build()` with `throw new ShepardPluginException(detail, BAD_REQUEST)`.

**Plugin or core?** The contract is in core (plugin-sdk); the fixes are per-plugin. All 11 plugins affected by the error shape; 4 affected by missing pagination fields.

**Effort:** M (1 week: 2 days for plugin-sdk shared types; 1 day per affected plugin to adopt them — 4 plugins × 1 day = 4 days; total ~6 days spread across teams).

**Domain impact:** All SDK consumers, all AI agents that call plugin endpoints. AAS plugin is critical for the MFFD Asset Administration Shell integration.

**Cross-finding hook:** api-scrutinizer §"Error Shape Consistency" (Proposal 7 fixes core; this fixes plugins), Proposal 5 (pagination envelope must be consistent across core and plugins for a generated SDK to be usable), strategy-advisor §2 "Where Shepard Lags — UI completeness" (a well-designed SDK makes the backend-only features accessible to non-UI callers until the UI ships).

---

## Summary Matrix

| # | Proposal | Effort | Priority | Blocks/Unlocks |
|---|---|---|---|---|
| 1 | Typed container arrays in DataObjectV2IO | S | CRITICAL | MCP server, manifest generator, all agent traversal |
| 2 | Flat GET /v2/data-objects/{appId} | S | CRITICAL | Ancestor chain walk, predecessor-status gate |
| 3 | POST /v2/import/jobs (execute import) | M | CRITICAL | Agentic ingest, MFFD pipeline, seed refresh |
| 4 | Ancestor chain endpoint | M | HIGH | EN 9100 audit trace, PLUTO causal chain |
| 5 | Unified pagination + status filter | S | HIGH | Curator bulk workflows, NCR status filtering |
| 6 | Metadata completeness score | M | HIGH | FAIR compliance, HMC call, Unhide feed license |
| 7 | ProblemJson everywhere | S | HIGH | All SDK consumers, MCP error handling |
| 8 | Human-readable OpenAPI tags | XS | MEDIUM | SDK class names, MCP tool naming |
| 9 | TS-IDa + TS-IDb (channel appId) | M | HIGH | All ML pipelines, live-window memory fix |
| 10 | PDF auto-annotation endpoint | M | MEDIUM | Annotation throughput, MFFD ingest UX |
| 11 | Anomaly digest endpoint | S | MEDIUM | QC overview, project manager dashboard |
| 12 | Plugin pagination + error contracts | M | MEDIUM | Plugin SDK adoption, AAS/video/git callers |

**Dependency order for a single-sprint focus:** 1 → 2 → 7 → 8 (these four are S/XS and have no external blockers). Then 9 (unblocks everything ML). Then 3 (completes the import loop). The remaining proposals are each independently shippable.
