---
stage: fragment
last-stage-change: 2026-06-30
---

# APISIMP Sweep — 2026-06-30 (fire-332)

**Scope:** OpenAPI `@Tag` fragmentation across the `/v2/` REST surface (excluding `/v2/admin/` — cleaned by fire-330/331).

**Method:** `grep -rn "@Tag(name" backend/src/main/java/de/dlr/shepard/v2/ --include="*.java"`, then grouped by tag-name and compared against sibling resources.

**Baseline after fire-331 admin tag consolidation:** `/v2/admin/` is now fully consolidated under `"Admin"` (26 resources, with #2206 + #2207 just merged). The same consolidation work is needed for the non-admin surface.

---

## Findings

### F1 — Collection tag fragmentation (15 files, 13 sub-tags) [APISIMP-COLLECTION-TAG-CONSOLIDATION]

**Severity:** MAJOR. The "Collections" tag in OpenAPI currently shows only the 8 CRUD endpoints from `CollectionV2Rest`. The remaining 15 Collection-scoped REST resources each use a bespoke "Collection *" sub-tag, creating 13 isolated sidebar sections in Swagger UI and breaking SDK generator grouping.

**Pattern:** `"DataObjects"` consolidates 4 resources cleanly under one tag. Collections should mirror this.

| File | Current tag | Target |
|------|-------------|--------|
| `CollectionEventsRest.java:49` | `"Collection events"` | `"Collections"` |
| `CollectionExportUrlRest.java:65` | `"Collection export URL"` | `"Collections"` |
| `CollectionSceneGraphRest.java:67` | `"Collection hero-view"` | `"Collections"` |
| `CollectionLabJournalEntriesRest.java:66` | `"Collection lab journal entries"` | `"Collections"` |
| `CollectionPublicationStateRest.java:57` | `"Collection lifecycle"` | `"Collections"` |
| `CollectionPropertiesRest.java:45` | `"Collection properties"` | `"Collections"` |
| `CollectionContainersRest.java:44` | `"Collection referenced containers"` | `"Collections"` |
| `RepExportV2Rest.java:53` | `"Collection regulatory evidence pack"` | `"Collections"` |
| `CollectionStreamExportRest.java:41` | `"Collection stream export"` | `"Collections"` |
| `CollectionTemplatesRest.java:66` | `"Collection templates"` | `"Collections"` |
| `TemplateInstantiationRest.java:80` | `"Collection templates"` | `"Collections"` |
| `CollectionCrossTimelineRest.java:67` | `"Collection timeline"` | `"Collections"` |
| `CollectionTimelineRest.java:68` | `"Collection timeline"` | `"Collections"` |
| `CollectionWatchesRest.java:55` | `"Collection watched containers"` | `"Collections"` |
| `CollectionWatchersRest.java:55` | `"Collection watches"` | `"Collections"` |

**AC:** No `@Tag(name = "Collection *")` sub-tags remain; all `/v2/collections/...` endpoints appear under `"Collections"` in the OpenAPI spec; `mvn verify -pl backend` green.

---

### F2 — Semantic tag fragmentation (6 files, 6 sub-tags) [APISIMP-SEMANTIC-TAG-CONSOLIDATION]

**Severity:** MAJOR. The semantic surface uses 6 different tags — `"Semantic annotations"`, `"Semantic SPARQL proxy"`, `"Semantic predicate statistics"`, `"Semantic term search"`, `"Semantic vocabularies"`, and `"Ontology alignment registry"` — producing 6 separate OpenAPI sections for what is effectively one coherent feature domain. A canonical `"Semantics"` tag unifies them.

| File | Current tag | Target |
|------|-------------|--------|
| `SemanticAnnotationV2Rest.java:80` | `"Semantic annotations"` | `"Semantics"` |
| `SemanticSparqlRest.java:98` | `"Semantic SPARQL proxy"` | `"Semantics"` |
| `SemanticPredicateStatsRest.java:64` | `"Semantic predicate statistics"` | `"Semantics"` |
| `SemanticTermSearchRest.java:60` | `"Semantic term search"` | `"Semantics"` |
| `VocabularyBrowseRest.java:61` | `"Semantic vocabularies"` | `"Semantics"` |
| `OntologyAlignmentRest.java:51` | `"Ontology alignment registry"` | `"Semantics"` |

**AC:** All semantic-surface endpoints appear under `"Semantics"` tag; no standalone "Semantic *" or "Ontology *" sections remain; `mvn verify -pl backend` green.

---

### F3 — Timeseries tag fragmentation (3 files) [APISIMP-TIMESERIES-TAG-CONSOLIDATION]

**Severity:** MINOR. Three timeseries-adjacent resources use distinct sub-tags, splitting timeseries content across sidebar sections.

| File | Current tag | Target |
|------|-------------|--------|
| `SqlTimeseriesRest.java:133` | `"Timeseries SQL"` | `"Timeseries"` |
| `AnomalyDetectionRest.java:51` | `"Timeseries annotations"` | `"Timeseries"` |
| `CrossDoBulkDataRest.java:60` | `"Timeseries cross-DataObject view"` | `"Timeseries"` |

Note: confirm that a canonical `"Timeseries"` tag exists on the main timeseries channel resource before dispatching (avoid introducing yet another orphan tag). If the main TS resource uses a different tag, use that as the target.

---

### F4 — Misc single-file tag mismatches (5 files) [APISIMP-MISC-TAG-FIXES]

**Severity:** MINOR. Five isolated resources use tags that diverge from the clearly-intended group tag already used by sibling resources.

| File | Current tag | Target | Reason |
|------|-------------|--------|--------|
| `ContainerPublicationStateRest.java:56` | `"Container lifecycle"` | `"Containers"` | `ContainersV2Rest` uses `"Containers"` |
| `ReferenceAnnotationRest.java:56` | `"Reference annotations"` | `"References"` | `ReferencesV2Rest` uses `"References"` |
| `CollectionDQRRest.java:57` | `"Data quality requirements"` | `"Data quality"` | `IndependenceProofRest` uses `"Data quality"` |
| `ImportJobsV2Rest.java:64` | `"Import jobs"` | `"Import"` | 3 sibling import resources use `"Import"` |
| `FileBundleReferenceRest.java:88` | `"File bundles"` | `"References"` | Bundle references are References on the v2 surface |

**AC:** All five files use their target group tag; no isolated "File bundles", "Import jobs", "Container lifecycle", "Reference annotations", or "Data quality requirements" sections remain; `mvn verify -pl backend` green.

---

## Summary table

| Row ID | Files | Severity | Status |
|--------|-------|----------|--------|
| APISIMP-COLLECTION-TAG-CONSOLIDATION | 15 | MAJOR | ⏳ queued |
| APISIMP-SEMANTIC-TAG-CONSOLIDATION | 6 | MAJOR | ⏳ queued |
| APISIMP-TIMESERIES-TAG-CONSOLIDATION | 3 | MINOR | ⏳ queued |
| APISIMP-MISC-TAG-FIXES | 5 | MINOR | ⏳ queued |

All are annotation-only changes — no wire-shape impact, no migration, no frontend changes needed. Dispatch order: Collection first (highest impact), then Semantic, then bundle Timeseries + Misc into one PR.

**Excluded from this sweep:** Admin surface (cleaned fire-330/331); plugin @Tag values (plugin admin REST already uses `"Admin"` per V2CONV-A7 shape); new `@Path(Constants.SHEPARD_API + ...)` — none found.
