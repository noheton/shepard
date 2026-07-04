---
stage: fragment
date: 2026-06-24
author: claude-sonnet-4-6
---

# API Simplification Sweep — 2026-06-24

Read-only sweep of `backend/src/main/java/de/dlr/shepard/v2/` (83 REST files) and all
plugin backends (20 REST files). Checked for the seven APISIMP smell categories in
priority order. No Java code was modified.

## What I Found

| Finding ID | Severity | File:Line | Description | Recommended Fix |
|---|---|---|---|---|
| APISIMP-DO-IO-NUMERIC-ID-LEAK | MAJOR | `v2/dataobject/io/DataObjectDetailV2IO.java:37`<br>`v2/dataobject/io/DataObjectListItemV2IO.java:31` | Both classes use `@JsonIgnoreProperties({"id"})` but inherit 7 numeric Neo4j id arrays from the frozen `DataObjectIO`: `collectionId` (long), `referenceIds[]` (long[]), `successorIds[]` (long[]), `predecessorIds[]` (long[]), `childrenIds[]` (long[]), `parentId` (Long), `incomingIds[]` (long[]). All 7 flow through the v2 wire today. Note: the v2 IO already adds typed appId replacements (`timeseriesReferenceAppIds`, `fileReferenceAppIds`, `structuredDataReferenceAppIds`, `predecessorSummaries`, etc.) making the numeric arrays redundant for v2 callers. | Extend `@JsonIgnoreProperties` to `{"id", "collectionId", "referenceIds", "successorIds", "predecessorIds", "childrenIds", "parentId", "incomingIds"}`. This is safe: every numeric array already has an appId-keyed counterpart on the v2 IO. Verify no v2 caller reads the numeric fields before landing. |
| APISIMP-COLL-IO-NUMERIC-ID-LEAK | MAJOR | `v2/collection/io/CollectionV2IO.java:22` | Suppresses only the bare `id` field. The three numeric fields inherited from the frozen `CollectionIO` still flow through the v2 wire: `dataObjectIds[]` (long[]), `incomingIds[]` (long[]), and `defaultFileContainerId` (Long, already deprecated on the v1 IO). The v2 collection list endpoint comments note `dataObjectIds[]` as "legacy long ids" (CollectionV2Rest.java:188) but they still appear in the response body. | Extend to `@JsonIgnoreProperties({"id", "dataObjectIds", "incomingIds", "defaultFileContainerId"})`. These are all either superseded (appId lists exist) or deprecated. Coordinate with any v2 client that might still read `dataObjectIds` before landing — the PR comment at line 188 suggests it was already flagged as legacy. |
| APISIMP-SPATIAL-V1-NUMERIC-IDS | CRITICAL* | `plugins/spatiotemporal/.../SpatialDataReferenceRest.java` (multiple PathParams)<br>`plugins/spatiotemporal/.../SpatialDataPointRest.java` (multiple PathParams) | Both use `Constants.SHEPARD_API` path prefix with `@PathParam Long collectionId`, `Long dataObjectId`, `Long spatialDataReferenceId`, `Long containerId`. These are genuine numeric Neo4j id params on the wire under the v1 frozen surface. *Marked CRITICAL for severity of the smell but this is a **documented upstream compat exception** — the v1 spatial endpoints must remain unchanged to preserve byte-compat with upstream shepard 5.2.0 third-party clients. The v2 sibling shelf is tracked as SPATIAL-V6-003 + PLUGIN-V2-001. The in-context promote endpoint (`SpatialPromoteRest.java`) correctly uses `/v2/spatial/promote` with appId strings. | No change to the v1 surface. Accelerate SPATIAL-V6-003: ship the `/v2/spatial/{appId}` sibling endpoints and migrate this fork's own callers to them. Once the v2 shelf is complete, the v1 endpoints become compat-only carry with no new DLR callers. |
| APISIMP-DO-ROOT-PATH-INCONSISTENCY | MINOR | `v2/dataobject/resources/DataObjectV2Rest.java` — `/v2/collections/{collectionAppId}/data-objects`<br>`v2/dataobject/resources/DataObjectBatchV2Rest.java` — `/v2/data-objects/batch`<br>`v2/dataobject/resources/DataObjectRdfRest.java` — `/v2/data-objects/{appId}/rdf` | The main CRUD surface is collection-scoped (`/v2/collections/{id}/data-objects`), while the batch and RDF endpoints are at a flat `/v2/data-objects/...` root. This breaks the resource hierarchy expectation: a caller navigating from a collection to its data objects via the API tree won't discover batch or RDF operations at a sibling path. The batch endpoint is plausibly cross-collection (operating on appIds directly), but the RDF endpoint operates on a single DataObject appId and has no reason to be at a different root than `GET /v2/collections/{id}/data-objects/{appId}/rdf`. | For the RDF endpoint: add a sibling route at `/v2/collections/{collectionAppId}/data-objects/{appId}/rdf` (or redirect) so the collection-scoped tree is complete. For the batch endpoint: document explicitly in the OpenAPI description that it is intentionally collection-agnostic (operates on appIds directly). Add a comment in `DataObjectBatchV2Rest.java` stating the design decision. |
| APISIMP-TOMBSTONE-REMOVAL-WINDOW | MINOR | `v2/file/resources/FileReferenceV2Rest.java` (all methods return 410)<br>`plugins/video/.../VideoStreamReferenceV2Rest.java` (all methods return 410)<br>`plugins/git/.../GitReferenceRest.java` (all methods return 410)<br>`plugins/wiki-writer/.../WikiWriterTombstoneRest.java` (all methods return 410) | Four tombstone REST classes return 410 Gone. None have a removal window defined in the backlog (`aidocs/16`). Tombstones that live indefinitely become permanent dead weight — they still register JAX-RS routes, consume classpath scanning time, and confuse contributors who grep for the path. APISIMP-FILE-PATH-RETIRE-2 is listed as "done" (PR #1978) but no follow-up removal ticket exists. | File a `APISIMP-TOMBSTONE-REMOVAL-2026` backlog row for each tombstone class. Set a target removal release (suggest: next minor after current). The 410 body should include the replacement path in the `Location` response header so clients can migrate. |
| APISIMP-PROVENANCE-CURSOR-UNDOCUMENTED | MINOR | `v2/provenance/resources/ProvenanceRest.java` — `@QueryParam("since")`, `@QueryParam("until")`, `@QueryParam("pageSize")` | ProvenanceRest uses time-cursor pagination (`since`/`until` epoch ms + `pageSize` cap) rather than the `page` + `pageSize` convention used by every other list endpoint in `/v2/`. The design is correct for an append-only event stream (cursor avoids pagination drift as new activities land), but it is not documented as intentionally different from the standard convention. A caller who tries `?page=0&pageSize=20` gets no error — the `page` param is silently ignored and the endpoint returns the full window. | Add an explicit OpenAPI `@Operation` note stating: "This endpoint uses time-cursor pagination (`since`/`until` epoch ms) rather than page-offset, because the activity stream is append-only and offset pagination would produce inconsistent results under concurrent writes." Consider returning 400 if `page` is supplied, to prevent silent param mismatch. Add a backlog row `APISIMP-PROVENANCE-CURSOR-DOC` to track the documentation update. |

_* CRITICAL severity reflects the smell category (numeric Neo4j ids on the wire); the finding itself is a known documented exception and requires no immediate code change._

## Summary

| Severity | Count |
|---|---|
| CRITICAL (documented exception, no code change needed) | 1 |
| MAJOR | 2 |
| MINOR | 3 |
| **Total** | **6** |

### Top 3 Highest-ROI Fixes

1. **APISIMP-DO-IO-NUMERIC-ID-LEAK** (MAJOR) — Extending `@JsonIgnoreProperties` on `DataObjectDetailV2IO` and `DataObjectListItemV2IO` to cover all 7 inherited numeric id fields is a one-line change per class that immediately stops Neo4j internal node IDs from crossing the v2 wire. The v2 IO already provides typed appId replacements for every suppressed field, so no caller loses data. This is the single highest-leverage change in the sweep: two files, two annotation edits, complete numeric-id elimination from the most-used v2 response shape.

2. **APISIMP-COLL-IO-NUMERIC-ID-LEAK** (MAJOR) — Same pattern as above on `CollectionV2IO`. One annotation edit suppresses `dataObjectIds[]`, `incomingIds[]`, and the already-deprecated `defaultFileContainerId`. The collection list endpoint comment at line 188 already flags `dataObjectIds[]` as "legacy long ids" — the suppression completes that intention.

3. **APISIMP-DO-ROOT-PATH-INCONSISTENCY** (MINOR) — Adding a sibling route on `DataObjectRdfRest` at the collection-scoped path, and adding a one-line doc comment to `DataObjectBatchV2Rest` stating the cross-collection design intent. Low effort, high discoverability benefit for API consumers navigating the resource tree.

### Existing Backlog Entries Not Re-filed (Already Tracked)

- `APISIMP-PERMISSION-AUDIT-NEO4J-ID` — queued (blocked on L2 migration)
- `APISIMP-COLLECTION-LIST-USES-SIZE-NOT-PAGESIZE` — in-progress (PR #2028)
- `APISIMP-BUNDLE-GROUP-FILES-SIZE-PAGESIZE` — in-progress (PR #2028)
- `APISIMP-FILE-PATH-RETIRE-2` — done (PR #1978)
- `APISIMP-STALE-COMMENTS` — done
- `APISIMP-REF-CREATE-NUMERIC-IDS` — done (PR #1965)
