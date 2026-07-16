---
stage: deployed
last-stage-change: 2026-07-16
fire: fire-634
---

# APISIMP Sweep — 2026-07-16 (fire-634)

Post-fire-634 merge of PR #2604 (APISIMP-CONT-LIST-INMEM-PAGING). This sweep scans the
v2 REST surface for remaining in-memory subList/count patterns after all fire-631/633/634
fixes landed.

## Scan scope

`backend/src/main/java/de/dlr/shepard/v2/` — all `.java` files. Grep: `.subList(`, `.size()` as
totals, SKIP/LIMIT absence on paginated list endpoints.

## Confirmed clean (intentional or bounded)

| Location | Pattern | Verdict |
|---|---|---|
| `AccessibleUrdfService.java:102` | post-permission-filter subList on Cypher-bounded result | ✅ Intentional — permission filter must run in Java; DB query already capped at `MAX_CANDIDATES` (PR #2601) |
| `AdminConfigRest.java:100-102` | subList on ConfigRegistry feature list | ✅ Bounded — feature count is compile-time-fixed (< 20 entries) |
| `SemanticAdminRest.java:295` | subList on uploaded ontology list | ✅ Bounded — deployment has < 100 ontologies |
| `PluginsAdminRest.java:149` | subList on plugin list | ✅ Bounded — plugin count < 50 |
| `SnapshotDiffRest.java:204,208,212` | safety-cap subList on diff lists | ✅ Capped — not pagination; prevents unbounded diff response |
| `CrossDoBulkDataRest.java:211` | subList on bulk channel data | ✅ Bounded by request body |
| `ImportDiagnosticsV2Rest.java:163` | `truncated ? all.subList(0, maxItems) : all` | ✅ Safety cap, not pagination |
| `SearchMcpTools.java:240` | post-permission subList after Cypher LIMIT 500 | ✅ Bounded — Cypher query caps at 500 before permission filter |
| `ContainerKindHandler.java:174,312` | SPI default fallbacks | ✅ All 3 in-tree handlers (File/TS/SD) override `count(nameFilter)` and `list(nameFilter, skip, limit)` — defaults only hit by plugin handlers that don't override |
| `CollectionDQRRest.java:208` | truncation safety cap | ✅ Bounded |

## New findings

### F1 — APISIMP-REFS-HANDLER-PAGING-TAIL (size: S)

**Location:** `ReferenceKindHandler.java:142` (default `countByDataObject`) and
`ReferenceKindHandler.java:161` (default `listByDataObject(skip,limit)`). Both SPI
defaults still used by 5 concrete in-tree handlers:

- `TimeseriesReferenceKindHandler` — does NOT override `countByDataObject` or `listByDataObject(skip, limit)`
- `FileBundleReferenceKindHandler` — same
- `StructuredDataReferenceKindHandler` — same
- `CollectionReferenceKindHandler` — same
- `DataObjectReferenceKindHandler` — same

**Impact:** `GET /v2/references?kind=timeseries&dataObjectAppId=...&page=N` dispatches to:
1. `referencesService.countByDataObject("timeseries", doAppId, null)` → `TimeseriesReferenceKindHandler`
   → SPI default → `listByDataObject(all).size()` → `getAllReferencesByDataObjectId` (unbounded Cypher) → load all into memory → `.size()`
2. `referencesService.listByDataObject("timeseries", doAppId, null, skip, pageSize)` → SPI default
   → same unbounded Cypher load → `.subList(skip, to)`

For a DataObject with 100 TimeSeries references, every paged request performs two full O(N) scans.
FireBundle/SD/Collection/DO references are smaller in practice (< 10 per DO) but share the same path.

**Fix:** Add `countByDataObject(appId, subKind)` and `listByDataObject(appId, subKind, skip, limit)`
overrides to each handler, delegating to their respective service methods (`countByDataObjectAppId` /
`listByDataObjectAppId`). The same shape as FileReferenceKindHandler (fire-633, PR #2603) and
UriReferenceKindHandler. The underlying service methods need `skip+limit` signatures if they don't exist.

**First refs:** `ReferenceKindHandler.java:141-162`; `TimeseriesReferenceKindHandler.java:223`
(only overrides full-list variant); `ReferencesV2Rest.java:550-551` (call site).

### F2 — APISIMP-MCP-ANNOT-INMEM (size: XS)

**Location:** `ContentMcpTools.java:211-215`

`list_annotations` MCP tool calls `semanticAnnotationService.getAllAnnotationsByShepardId(ogmId)`
(unbounded Cypher, loads ALL SemanticAnnotations for the DataObject) then paginates in Java:
```java
List<SemanticAnnotation> annotations = semanticAnnotationService.getAllAnnotationsByShepardId(ogmId);
int total = annotations.size();
List<SemanticAnnotation> page1 = annotations.subList(from, to);
```

**Impact:** Low (annotations bounded per DO in practice; MCP is interactive). But on a
MFFD DataObject with 200+ annotations from AI bulk-annotation, every `page=0` call loads all.

**Fix:** Add `getAllAnnotationsByShepardId(ogmId, skip, limit)` overload to
`SemanticAnnotationService` + Cypher DAO (`SKIP $skip LIMIT $limit`), wire it through
`ContentMcpTools`.

**First refs:** `ContentMcpTools.java:211`; `semantics/SemanticAnnotationService.java` (locate
the `getAllAnnotationsByShepardId` method).

## Summary

All in-tree container handlers are clean (PR #2604). File+URI reference handlers are clean
(PR #2603). The APISIMP paging mandate is largely satisfied; two tail findings remain:

| ID | Size | Status |
|---|---|---|
| APISIMP-REFS-HANDLER-PAGING-TAIL | S | queued (fire-634 sweep) |
| APISIMP-MCP-ANNOT-INMEM | XS | queued (fire-634 sweep) |

No other unbounded-subList patterns found in the v2 REST surface.
