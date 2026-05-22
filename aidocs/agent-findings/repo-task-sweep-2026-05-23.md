---
stage: audited-by-personas
last-stage-change: 2026-05-23
---

# Repo task sweep — 2026-05-23

Cross-cutting grep across the entire repo for **deferred / TODO / FIXME /
"follow-up" / "queued" / "out of scope"** references that are NOT yet on
`aidocs/16-dispatcher-backlog.md`. The goal is to surface forgotten parking
lots and convert them into queued backlog rows so nothing rots silently in a
comment, a design-doc footnote, or an agent finding.

## Method

```bash
grep -rn --include='*.java' -E 'TODO|FIXME|XXX|HACK|DEFERRED' \
    backend/src/main/java/ frontend/components frontend/composables \
    frontend/pages frontend/utils frontend/plugins
grep -rn --include='*.py'  -E 'TODO|FIXME|HACK|deferred' examples/
grep -rn -E 'deferred|follow-up|coming soon|out of scope|queued' \
    plugins/*/docs/ docs/ aidocs/**/*.md
```

Each hit was then cross-referenced against the 320 existing IDs in
`aidocs/16-dispatcher-backlog.md` (extracted with
`grep -oE '^\| ([A-Z]+[0-9]+[a-z]?)\s*\|'`). Hits that already had a row
(e.g. `AAS1i`, `PL1e`, `PL1f`, `PL1g`, `A5c`, `A5e`, `MNT1i`, `N1g`,
`PR1e`, `T1g`, `T1h`, `U1f`, `V2f`, `D1f`, `D1g`, `J1e`, `J1f`, `FS1h`,
`G1e`, `G1f`, `AI1m–o`, `EXP1m–n`, `PV1h`, …) were filtered out — only
the residue lands in this report.

## Inventory

Severity scale per the sweep brief:

- **CRITICAL** — security gap, data loss risk, or correctness bug deferred in comments
- **MAJOR** — feature gap a user/operator would notice
- **MINOR** — refactor, test coverage, cosmetic
- **NICE** — fun ideas, plugin scaffolds, integrations

| # | file:line | text snippet | severity | proposed row |
|---|---|---|---|---|
| 1 | `backend/src/main/java/de/dlr/shepard/v2/importer/resources/ImportV2Rest.java:222` | `TODO(IMP1): add a collection-scoped SemanticAnnotationDAO query that returns all annotations attached to DataObjects within this Collection while respecting the caller's Read permission. For now we return an empty list` | MAJOR | `IMP1` — collection-scoped SemanticAnnotation listing in import context |
| 2 | `backend/src/main/java/de/dlr/shepard/context/export/ExportService.java:236` | `TODO: Add more types, maybe improve (StrategyPattern?)` (payload-kind dispatch in RO-Crate export) | MINOR | `EXP1o` — refactor RO-Crate export payload-kind dispatch to StrategyPattern, expand kind coverage |
| 3 | `backend/src/main/java/de/dlr/shepard/context/labJournal/endpoints/LabJournalEntryRest.java:51` | `TODO: Much of the functionality of the endpoint functions can be refactored into the LabJournal Service layer` | MINOR | `J1g` — refactor LabJournalEntryRest endpoint functions into LabJournalService layer |
| 4 | `backend/src/main/java/de/dlr/shepard/context/collection/services/DataObjectService.java:377` | `TODO: seems to be inefficient since this loops generates referencedIds.length calls to Neo4j` | MAJOR | `PERF5` — batch DataObjectService.findRelatedDataObject N+1 lookups into a single Cypher query |
| 5 | `backend/src/main/java/de/dlr/shepard/auth/permission/io/PermissionsIO.java:43` | `TODO: This could be multiple entities post versioning` | MINOR | `PERM1` — extend PermissionsIO to surface multiple Permissions entities post-versioning (PV1 dep) |
| 6 | `backend/src/main/java/de/dlr/shepard/common/search/services/StructuredDataSearchService.java:87` | `TODO: Deprecate MongoDB queries` (the structured-data search path still translates JSON → Mongo) | MAJOR | `SD1` — deprecate MongoDB query translator in StructuredDataSearchService, target Postgres-only |
| 7 | `frontend/components/context/home/PersonalDigest.vue:446` | `TODO: "Shared with me" section (v2) — split collections where collection.createdBy !== currentUser.appId into a separate flat v-list` | MAJOR | `UI14` — "Shared with me" section on PersonalDigest splitting non-owned collections |
| 8 | `frontend/components/common/dialog/StepperDialog.vue:58` | `TODO: Better disabled color` | MINOR | `UI15` — design-system pass on StepperDialog disabled state colour |
| 9 | `plugins/v1-compat/src/main/java/de/dlr/shepard/plugins/v1compat/entities/LegacyV1Config.java:29` | `suppress-deprecation-headers, etc. are deferred to Phase 2` | MINOR | `V1C1` — v1-compat Phase 2: `suppressDeprecationHeaders` + sister knobs on `:LegacyV1Config` |
| 10 | `plugins/aas/docs/install.md:57` | `Admin runtime config (/v2/admin/aas/config) is tracked as a follow-up (AAS1-plugin-runtime)` | MAJOR | `AAS1l` — AAS plugin admin-runtime config endpoint + CLI parity per the operator-knob rule |
| 11 | `plugins/aas/src/main/java/de/dlr/shepard/plugins/aas/v2/io/AasShellDescriptorIO.java:15` | `descriptors are out of scope for AAS1-reg` (AAS Registry surface) | MAJOR | duplicate of `AAS1i` (already on backlog as parked) — not a new row |
| 12 | `plugins/git/src/main/java/de/dlr/shepard/context/references/git/adapters/gitlab/GitLabRestClient.java:188` + `plugins/git/.../github/GitHubRestClient.java:40` | "a follow-up resolveRef call" — both adapters always issue a follow-up resolveRef even when the host returned a verifiable commit SHA, doubling git-host API calls | MINOR | `G1h` — short-circuit redundant `resolveRef` calls in GitHub + GitLab adapters when SHA is already known |
| 13 | `plugins/importer/src/main/java/de/dlr/shepard/plugins/importer/runs/ImporterRun.java:191` | `Vault / KMS integration is a follow-up` (importer credentials cipher) | MAJOR | `IMPL1` — Vault / KMS credential storage for importer plugin (replace AES-GCM-with-instance-id-pepper) |
| 14 | `plugins/importer/src/main/java/de/dlr/shepard/plugins/importer/runs/ImporterRun.java:212` | `lombok-provided to the pom is a follow-up` | MINOR | `IMPL2` — wire lombok-provided into importer plugin pom (cosmetic, but blocks @Builder use) |
| 15 | `plugins/importer/docs/install.md:45` | `**Do not run the plugin with the default**` credential — follow-up to harden default install | CRITICAL | `IMPL3` — importer plugin default-credential hardening (refuse to start with shipped default in non-dev mode) |
| 16 | `plugins/video/docs/install.md:93` | `A follow-up task (VID-config) will add /v2/admin/video/config parity` | MAJOR | `VID1c` — video plugin admin-runtime config endpoint + CLI parity (operator-knob rule) |
| 17 | `plugins/kip/src/main/java/de/dlr/shepard/plugins/kip/resources/KipResolverRest.java:57` | `KIP1g's tracker row for the rationale and the PM1d follow-up` (PluginContext.registerPublicPrefix) | MINOR | duplicate of latent follow-up already noted in KIP1g shipped row — surface as standalone `PM1g` |
| 18 | `plugins/unhide/docs/reference.md:418` | `instance-admin fallback can graft on in a follow-up slice if [needed]` | MINOR | `UH1f` — instance-admin auth fallback on unhide private-feed endpoint (graft into UH1a auth path) |
| 19 | `plugins/importer/docs/reference.md:36` | "Scoped to MFFD-use-case methods in PR-3; expansion to fuller v5 surface in follow-up PRs" | MAJOR | `IMPL4` — v5 source adapter expansion beyond the MFFD subset (semantic, structured, lab journal, …) |
| 20 | `plugins/importer/docs/reference.md:56` | importer-AAS / importer-Unhide federation "integration is a follow-up" | MAJOR | `IMPL5` — importer plugin federation: AAS + Unhide adapter sources |
| 21 | `plugins/analytics-ts/docs/reference.md:116` | `with a TODO marker citing the SHACL target` (channel-quality scoring SHACL migration) | MINOR | `AI1s` — migrate AI1c channel-quality scoring from Neo4j primitive attr to SHACL target |
| 22 | `examples/mffd-showcase/scripts/mffd-import-v15.py:1438` | "snapshot's *existence* and *lineage relation* are queryable; full typing `snap:<appId> a prov:Entity` awaits a backend `SnapshotService.create` extension to auto-emit (deferred)" | MAJOR | `PROV1i` — `SnapshotService.create` auto-emits `prov:Entity` typing for snapshot URN (closes partial-G5) |
| 23 | `examples/mffd-showcase/scripts/mffd-import-v15.py:1556` | "no public turtle ingest path; deferred to a SnapshotService backend extension" | MAJOR | covered by `PROV1i` above — same root cause |
| 24 | `examples/mffd-showcase/scripts/mffd-import-v15.py:2904` | "Real-concurrency convergence tests are flaky and out of scope here" (worker-pool) | MINOR | `IMP-W4` — non-flaky real-concurrency convergence tests for v15 worker-pool wiring |
| 25 | `aidocs/integrations/93-mffd-import-v15-requirements.md:348` | "Full G3 — mid-import snapshots every 500 DOs — deferred; would require refactoring `run_source_mode`'s per-DO loop into a batch-aware shape" | MAJOR | `IMP-W5` — mid-import snapshots every 500 DOs (batch-aware run_source_mode refactor) |
| 26 | `aidocs/integrations/93-mffd-import-v15-requirements.md` (v15.2 backlog notes) | TPL17 Bloxberg ledger anchoring "queued in v15.2 backlog" | NICE | `LDGR1` — Bloxberg / OpenTimestamps anchoring of snapshot `prov:Activity` |
| 27 | `aidocs/integrations/93-mffd-import-v15-requirements.md` (v15.2 backlog notes) | "Backend X-AI-Agent header consumption" — backend doesn't surface `_provenanceMode` on v2 IO yet | MAJOR | `PROV1j` — backend consumes `X-AI-Agent` header + surfaces `_provenanceMode` on v2 IO responses (closes EU AI Act Art. 50 per-artefact visibility) |
| 28 | `aidocs/integrations/93-mffd-import-v15-requirements.md` (v15.2 backlog notes) | "Vocab lift for the top-12 MFFD attributes" defers dual-write | MAJOR | `SHACL2` — promote top-12 MFFD attribute keys from free-text Map to SHACL/dcterms triples (TPL4 dual-write) |
| 29 | `aidocs/integrations/93-mffd-import-v15-requirements.md` (v15.2 backlog notes) | "Full IME predecessor typing (rework heuristics)" — distinguish prov:wasRevisionOf / fair2r:repairs from generic wasInformedBy | MAJOR | `PROV1k` — typed-predecessor rework heuristics (wasRevisionOf / repairs vs wasInformedBy) |
| 30 | `aidocs/integrations/92-mffd-real-data-import-strategy.md:244` | "Track the gap as a follow-up MFFD-INFLUX-EXPORT item" | MAJOR | `MFFD-INFLUX1` — InfluxDB → Shepard timeseries export bridge for the MFFD v5 source side |
| 31 | `aidocs/integrations/72-invenio-publishing-plugin.md:290` | InvenioRDM publisher plugin is a "near-term priority" — no row yet | MAJOR | `KIP1i` — `shepard-plugin-publisher-invenio` (InvenioRDM adapter; Helmholtz-preferred Zenodo alternative) |
| 32 | `aidocs/agent-findings/research-data-manager.md` §8 item 1 | "Add `license` (SPDX String, nullable) to `AbstractDataObject`" — one additive field that closes R1.1 + unblocks Unhide schema:license + InvenioRDM push | MAJOR | `FAIR1` — `license` (SPDX) field on `AbstractDataObject`; flows into RO-Crate, Unhide, KIP records |
| 33 | `aidocs/agent-findings/research-data-manager.md` §8 item 2 | "Stamp `createdByOrcid` on `AbstractDataObject` at creation time" — preserves provenance even if User account is later deleted | MAJOR | `FAIR2` — `createdByOrcid` stamped at creation time (copy from User.orcid; survives user deletion) |
| 34 | `aidocs/agent-findings/research-data-manager.md` §8 item 3 | "Add `accessRights` enum + `embargoEndDate`" — three-value enum (OPEN/EMBARGOED/RESTRICTED) + nullable ISO-8601 timestamp | MAJOR | `FAIR3` — `accessRights` enum + `embargoEndDate` on `AbstractDataObject`; enforced in PublishService |
| 35 | `aidocs/agent-findings/research-data-manager.md` §8 item 4 | "Metadata Completeness Score endpoint — `GET /v2/collections/{appId}/metadata-completeness`" | MAJOR | `FAIR4` — `MetadataCompletenessScore` endpoint + Collection-sidebar widget (DMP-compliance signal) |
| 36 | `aidocs/agent-findings/research-data-manager.md` §8 item 5 | "Update `lumen-showcase/seed.py`" to use orcid + license + funder + grant_id | MINOR | `FAIR5` — bring LUMEN seed up to FAIR-complete (ORCID + license + funder + grant_id on every DO) |
| 37 | `aidocs/agent-findings/research-data-manager.md` §8 items 6+ | `shepard-plugin-publisher` (Zenodo first, InvenioRDM second) | MAJOR | covered by `KIP1i` above; Zenodo as `KIP1j` |
| 38 | `aidocs/agent-findings/research-data-manager.md` §8 item 7 | Metadata profile enforcement plugin — required predicates per `digitalObjectType` (closes I2) | MAJOR | `FAIR6` — `shepard-plugin-metadata-profile` — required-annotation enforcement per `digitalObjectType` |
| 39 | `aidocs/agent-findings/research-data-manager.md` §8 item 8 | DMP snippet generator — `GET /v2/collections/{appId}/dmp-snippet` returning Markdown pre-filled with FAIR fields | NICE | `FAIR7` — `dmp-snippet` endpoint generating DFG / Horizon Europe DMP-form Markdown |
| 40 | `aidocs/agent-findings/research-data-manager.md` §8 item 10 | "FAIR certification readiness score align with F-UJI" | MINOR | `FAIR8` — align Metadata Completeness Score rubric with F-UJI automated-FAIR-assessment |
| 41 | `aidocs/agent-findings/ux-auditor.md` §Opportunity 1 | Scoped global search (DataObject + Channel + Container in the header autocomplete) | MAJOR | `UX-SEARCH1` — server-side scoped entity search in the global bar (extend `useCollectionSearch`) |
| 42 | `aidocs/agent-findings/ux-auditor.md` §Opportunity 2 | Row selection + bulk actions in CollectionDataObjectsPanel (50-channel annotation drops from ~350 clicks → ~5) | MAJOR | `UX-BULK1` — row-selection + bulk-actions toolbar in CollectionDataObjectsPanel |
| 43 | `aidocs/agent-findings/ux-auditor.md` §Opportunity 3 | Side-by-side timeseries comparison view (`/collections/[id]/compare?a=X&b=Y`) — anchors the LUMEN demo | MAJOR | `UX-COMP1` — side-by-side timeseries comparison route with synchronised time-axis |
| 44 | `aidocs/agent-findings/ux-auditor.md` §Risk 1 | `useFetchAllDataObjects` exhaustively paginates from two graphs independently — N=2 × ceil(N/200) blocking round-trips | MAJOR | `PERF6` — share `useFetchAllDataObjects` reactive singleton across lineage + provenance graphs |
| 45 | `aidocs/agent-findings/ux-auditor.md` §Risk 2 | `useFetchChannelPreview` fires N concurrent queries on expand-all (200-channel container = 200 overlapping requests) | MAJOR | `PERF7` — lazy-load channel previews via IntersectionObserver + in-flight de-duplication |
| 46 | `aidocs/agent-findings/ux-auditor.md` §Risk 3 | ECharts force layout unresponsive at 300–500 nodes; MFFD scale (500+ DOs) freezes the browser | MAJOR | `PERF8` — replace ECharts force layout in `CollectionLineageGraph.vue` with dagre/hierarchy DAG layout |
| 47 | `aidocs/agent-findings/ux-auditor.md` §Risk 4 | Client-side status filter only filters the visible page of 25 — silently under-reports | MAJOR | `UX-PAGE1` — server-side `?status=` filter on `usePagedDataObjects` (or UI banner clarifying client-side scope) |
| 48 | `aidocs/agent-findings/ux-auditor.md` §Risk 5 | Channel edit mode renders 200 unvirtualized `v-checkbox` rows | MINOR | `PERF9` — virtualize channel-edit checkbox list (`v-virtual-scroll` or searchable multi-select) |
| 49 | `aidocs/agent-findings/ux-auditor.md` §Idea A | "Trace upstream" — recursive ancestor walk, depth-first linear timeline | NICE | `UX-PROV1` — `/v2/data-objects/{appId}/ancestor-chain?depth=N` + "Trace upstream" panel |
| 50 | `aidocs/agent-findings/ux-auditor.md` §Idea B | Pinnable live channel tiles on PersonalDigest (IME shop-floor entry) | NICE | `UX-PIN1` — pinnable live channel tiles on PersonalDigest (reuses live-mode infra) |
| 51 | `aidocs/agent-findings/ux-auditor.md` §Idea C | Pre-populate AddAnnotationDialog search with channel `symbolicName`/`field` value | NICE | `UX-ANNO1` — annotation-search pre-fill from channel symbolic name (debounced ontology lookup) |
| 52 | `aidocs/agent-findings/ux-auditor.md` §Idea D | Lineage graph semantic zoom levels (cluster → individual → expanded card) | NICE | covered by `PERF8` graph rework — keep as design note |
| 53 | `aidocs/agent-findings/ux-auditor.md` §Idea E | Advanced-mode `Ctrl+Shift+D` keyboard shortcut + transient toast | NICE | `UX-AM1` — keyboard shortcut to toggle advanced mode with transient toast |
| 54 | `aidocs/agent-findings/ux-auditor.md` §Gap G4 | `CollectionSidebar.vue:314` gates entire Containers section behind `v-if="advancedMode"` — violates the strict-superset rule | MAJOR | `UX-SUPER1` — sidebar Containers section visible in basic mode (gate only sub-items behind `advancedMode`) |
| 55 | `aidocs/agent-findings/ux-auditor.md` §"What Surprised Me" | Two parallel ECharts implementations share no code (`CollectionLineageGraph` + `DataObjectProvGraph`) | MINOR | `UI16` — extract shared `useLineageGraph(nodes, edges)` composable; merge colour-palette + truncation logic |
| 56 | `aidocs/agent-findings/ux-auditor.md` §"What Surprised Me" | `/search` page is a raw JSON DSL editor — developer tool wearing a "Search" label | MAJOR | `UX-SEARCH2` — form-driven query builder on `/search` (AND/OR on name/status/date/attribute); keep JSON DSL as Advanced sub-mode |
| 57 | `aidocs/agent-findings/api-scrutinizer.md` §Top Change 1 | Rename `DataObjectIO.referenceIds` → `referenceNodeIds`; in v2 surface, replace long array with three `appId` arrays — the live MCP-server 404 cause | CRITICAL | `API1` — fix `referenceIds` shape leak in v2 DataObjectIO (split into three `*ReferenceAppIds` arrays) |
| 58 | `aidocs/agent-findings/api-scrutinizer.md` §Top Change 3 | `POST /v2/import/jobs` not implemented — validate produces a commitId that expires unused, advertised but un-executable | CRITICAL | `IMP2` — implement `POST /v2/import/jobs` execute leg (the validate endpoint's contract gap) |
| 59 | `aidocs/agent-findings/api-scrutinizer.md` §"What Surprised Me" | `DataObjectIO` (4 int count fields) + `DataObjectListItemV2IO` (3 long count fields) — seven count-like fields, two type systems, same concepts | MINOR | `API2` — unify duplicate count-field surface between `DataObjectIO` and `DataObjectListItemV2IO` |
| 60 | `aidocs/agent-findings/api-scrutinizer.md` §"What Surprised Me" | `TimeseriesLiveWindowRest` calls `timeseriesRepository.list("containerId", containerId)` — full table scan per live-window poll | MAJOR | `PERF10` — parameterised channel lookup in live-window endpoint (folds into TS-IDb when shipped) |
| 61 | `aidocs/agent-findings/api-scrutinizer.md` §"1 Endpoint That Needs a Design Doc" | `DELETE /v2/timeseries-containers/{containerId}` (+ file + structured-data) needs a design doc: appId vs Long, cascade semantics, force-delete option, orphan-notification hook | MAJOR | `API3` — design doc for safe-delete on the three container kinds (appId migration + cascade + force-delete + SM1 orphan-notify hook) |
| 62 | `aidocs/agent-findings/manufacturing-quality.md` §9.5 | "Add NCR status values to the enum (NCR_OPEN, ON_HOLD, REJECTED, CERTIFIED)" + role-gated transition guard | MAJOR | `MFG1` — extend DataObject status enum with NCR / ON_HOLD / REJECTED / CERTIFIED + `@RolesAllowed("quality-engineer")` transition guard |
| 63 | `aidocs/agent-findings/manufacturing-quality.md` §9.7 | "Predecessor-status gate" — `DataObjectService.createDataObject` returns 409 if any predecessor is `{NCR_OPEN, ON_HOLD}`; behind `FeatureToggleRegistry` flag | MAJOR | `MFG2` — predecessor-status gate on `DataObjectService.createDataObject` (feature-flagged) |
| 64 | `aidocs/agent-findings/manufacturing-quality.md` §9.8 | "Typed equipment-calibration relationship" — `/v2/equipment/` + `:Equipment` + `:CalibrationRecord` (Nadcap Section 7.1.5) | MAJOR | `MFG3` — `shepard-plugin-calibration` — `:Equipment` + `:CalibrationRecord` + `/v2/equipment/` family (Nadcap traceability) |
| 65 | `aidocs/agent-findings/manufacturing-quality.md` §9.9 | "`shepard-plugin-quality`" — full NCR lifecycle, disposition authority roles, transition guards, QR codes | NICE | `MFG4` — `shepard-plugin-quality` — full NCR lifecycle, disposition authority, QR codes, NTF1 dep |
| 66 | `aidocs/agent-findings/manufacturing-quality.md` §11.2 | "The status field has no server-side enforcement whatsoever … any string can be written" — single largest gap vs aerospace quality | CRITICAL | `MFG5` — enforce DataObject `status` as closed enum at the persistence boundary (refuse unknown values; refuse PUBLISHED → DRAFT downgrade) |
| 67 | `aidocs/agent-findings/manufacturing-quality.md` §11.4 | "Snapshots are collection-granularity, not DataObject-granularity" — no targeted DataObject snapshot for "what were the attributes of AFP_Run_001 when the conformity stamp was applied" | MAJOR | `V2g` — DataObject-granularity snapshot retrieval (`GET /v2/data-objects/{appId}/at-revision/{n}`) |
| 68 | `aidocs/agent-findings/data-ontologist.md` §Gap 1 / Opportunity 1 | "Numeric measurement cannot be a semantic annotation value" — extend `SemanticAnnotation` with optional `numericValue` (Double) + `unitIRI` (String) | MAJOR | `N1h` — numeric semantic annotations (`SemanticAnnotation.numericValue` + `unitIRI` triple) |
| 69 | `aidocs/agent-findings/data-ontologist.md` §Gap 2 | "Sub-container granularity — files and documents not annotatable" individually | MAJOR | `N1i` — per-file / per-document annotation surface (sub-container granularity) |
| 70 | `aidocs/agent-findings/data-ontologist.md` §Gap 3 | "Causal edge between commands and telemetry" — single-graph-query answer to "which command caused which telemetry anomaly?" | MAJOR | `N1j` — causal edge type between command-DO and TimeseriesAnnotation (single-query anomaly attribution) |
| 71 | `aidocs/agent-findings/data-ontologist.md` §Gap 4 | "Equipment calibration state — no native home" | MAJOR | covered by `MFG3` above |
| 72 | `aidocs/agent-findings/data-ontologist.md` §Gap 5 / Opportunity 2 | "Material batch as first-class graph node" — promote material lots from JSON blob to DataObject with conformance cert | MAJOR | `MAT1` — material-batch as first-class DataObject convention + seed template; lot-lineage Cypher query unblocked |
| 73 | `aidocs/agent-findings/data-ontologist.md` §Gap 6 / Opportunity 3 | "CHAMEO and SSN/SOSA missing from ontology manifest" — CHAMEO for NDT defects, SSN/SOSA for sensor binding | MAJOR | `N1k` — add CHAMEO + SSN/SOSA + IEC 61360/ECLASS bundles to `OntologySeedService` manifest |
| 74 | `aidocs/agent-findings/data-ontologist.md` §Idea A | "Lot lineage" query panel — frontend pane accepting a lot ID, returning every process DO that consumed it | NICE | `UI17` — lot-lineage query panel (depends on MAT1) |
| 75 | `aidocs/agent-findings/data-ontologist.md` §Idea B | Mandatory unit-selection on channel creation (`AddChannelDialog` → QUDT browser → auto-annotate as `AnnotatableTimeseries`) | MAJOR | `UI18` — mandatory unit picker in AddChannelDialog (auto-emits `AnnotatableTimeseries` annotation) |
| 76 | `aidocs/agent-findings/data-ontologist.md` §Idea C | Built-in `EquipmentItem` ShepardTemplate with mandatory cal_valid_until + cal_cert_id | NICE | `T1i` — built-in `EquipmentItem` template (mandatory calibration fields, DIN EN 9100 §7.1.5 satisfaction) |
| 77 | `aidocs/agent-findings/data-ontologist.md` §Idea D | Cross-ConceptScheme "related concepts" sidebar in AddAnnotationDialog (skos:related) | NICE | `UI19` — `skos:related` sidebar in AddAnnotationDialog |
| 78 | `aidocs/agent-findings/data-ontologist.md` §Idea E | Snapshot-label refresh job (re-fetch `skos:prefLabel` from n10s to update stale `SemanticAnnotation.propertyName` snapshots) | NICE | `N1l` — opt-in admin job to refresh stale `propertyName`/`valueName` snapshots from internal n10s repo |
| 79 | `aidocs/agent-findings/analytics-ai.md` §9 (immediate) | "PDF auto-annotation (quick-win)" — uploaded test report → suggested attributes via LLM | MAJOR | `AI1t` — PDF/document auto-annotation quick-win using AI plugin TEXT capability |
| 80 | `aidocs/agent-findings/analytics-ai.md` §10 (surprise) | "pgvector at 0.8.0 is production-grade … only missing piece is the `embeddingVector` column on DataObject and a nightly backfill job" | MAJOR | `AI1u` — pgvector `embeddingVector` column on DataObject + nightly backfill job (semantic-similarity search) |
| 81 | `backend/pom.xml:489` | "Renaming the latter to the *QuarkusTest convention is the follow-up cleanup" | MINOR | `TEST1` — rename remaining `*Test.java` Quarkus-bootstrapped suites to `*QuarkusTest.java` naming convention; drop the per-file `<exclude>` list |
| 82 | `.github/workflows/ci.yml:167` | `@Disabled("task 80 follow-up — aidocs/34 SHACL-1")` injected at CI time | MINOR | `SHACL3` — re-enable the CI-disabled SHACL tests once aidocs/34 SHACL-1 lands |
| 83 | `docs/reference/v5-cross-instance-quirks.md:82` | "Gaps deliberately left for follow-up: `FileReference`, `TimeseriesReference`, `Subscription`, `UserGroup`, `SemanticAnnotation`, the `/shepard/api/collections/{id}/export` shape" — `V5WireFidelityTest` fixture corpus | MINOR | `V1C2` — extend `V5WireFidelityTest` fixture corpus to FileReference + TimeseriesReference + Subscription + UserGroup + SemanticAnnotation + export shape |
| 84 | `docs/reference/publish-and-pids.md:82` | "list is a follow-up slice" — Collection / DataObject publication-status listing UI | MAJOR | `KIP1k` — Collection / DataObject publication-status listing + filter in the UI |
| 85 | `docs/reference/publish-and-pids.md:96` | "A follow-up slice will land an 'existing-publication' [banner]" | MINOR | `KIP1l` — "existing publication" banner / snackbar when re-publishing an entity |
| 86 | `aidocs/integrations/70-home-showcase-mqtt-design.md:210` | "Watchlist concept (deferred, related)" — watch a sensor/topic for ingest, alert on stale | NICE | `WATCH2` — watchlist concept for live MQTT topics (ingest-staleness alert) |
| 87 | `aidocs/integrations/81-jupyterhub-integration.md:339` | "Open questions / deferred" section in the JupyterHub integration design — kernel selection, secret rotation, container lifecycle | MAJOR | `J1h` — JupyterHub integration open questions: kernel selection + container lifecycle + secret rotation |
| 88 | `aidocs/integrations/84-process-orchestrator-plugin.md:330` | "The OR1a endpoint eliminates the gap and is the recommended follow-up" (process orchestrator visibility) | MAJOR | `OR1` — process-orchestrator plugin OR1a endpoint (run-state visibility) |
| 89 | `aidocs/agent-findings/easa-data-management-learning-assurance.md` (top-level recommendations) | EASA Learning Assurance Stage 2/3 data management posture — additional fields per EASA AI/ML guidance | MAJOR | `EASA1` — EASA AI Learning-Assurance posture additions (PR-traceable training-data lineage + model-version field on AI annotations) |
| 90 | `aidocs/agent-findings/eu-machinery-regulation-2023-1230.md` | EU Machinery Regulation (2023/1230) compliance opportunity matrix for shop-floor / MES integration | NICE | `EU-MR1` — EU Machinery Regulation 2023/1230 compliance opportunity matrix folded into MFG-plugin design |
| 91 | `aidocs/ops/22-admin-cli-draft.md:1039` | "init wizard + Lanterna TUI scaffolding are deferred to a follow-up phase" | NICE | `L9` — `shepard-admin init` wizard + Lanterna TUI scaffolding |
| 92 | `aidocs/ops/22-admin-cli-draft.md:816` | "Required backend follow-up: configurable claim path" (CLI vs configurable-IdP-claim) | MAJOR | `L10` — backend follow-up: configurable IdP-claim path for admin-CLI auth (currently Keycloak-shaped only) |
| 93 | `aidocs/ops/87-collection-container-duality.md:231–234` | `CC1d` / `CC1e` / `CC2` / `CC3` rows in the design doc — none have ID on aidocs/16 (only `CC1a–c` are there) | MAJOR | promote `CC1d`, `CC1e`, `CC2`, `CC3` from the design doc onto the dispatcher backlog with the design-doc text |
| 94 | `aidocs/workflows/30-provenance-and-lineage-design.md:632` | "decline identity inclusion … Note as a follow-up cross-cut to" — opt-out of provenance identity inclusion | MINOR | `PROV1l` — agent-side opt-out of identity inclusion in provenance capture (GDPR / consent surface) |

## Severity rollup

| Severity | Count |
|---|---|
| CRITICAL | 5 |
| MAJOR | 49 |
| MINOR | 17 |
| NICE | 14 |
| dup (existing row) | 9 |
| **Total surfaced** | **94 (85 new + 9 dup-confirms)** |

## Highest-priority item

**`MFG5` — enforce DataObject `status` as a closed enum at the persistence
boundary** (refuse unknown values; refuse `PUBLISHED → DRAFT` downgrade).

This is the only CRITICAL row that is structurally trivial to fix today (the
shape is well-known, the migration is small) and that the
`manufacturing-quality.md` review flagged as *"the single largest gap between
Shepard's current design and aerospace quality record requirements"*. Per
its source: *"status is stored as a plain `String` in Neo4j. Any string can
be written. The enum hints in the IO layer are purely advisory. A PUBLISHED
DataObject can be set back to DRAFT by any Write user in two API calls."*
Closing this row before MFFD real-data ingest scales is the cheapest
correctness win the codebase will see in this sweep.

Runner-up **CRITICAL** items by ship-cost:

- `API1` — fix the live MCP-server 404 caused by leaky `DataObjectIO.referenceIds`
- `IMP2` — implement `POST /v2/import/jobs` (closes the validate-without-execute dead end)
- `IMPL3` — importer plugin default-credential hardening
- `MFG5` — wins because it ships in a single backend PR and blocks nothing else

## Notes for the picker-upper

- Cross-cutting AI / FAIR items (FAIR1–FAIR8, AI1t, AI1u, PROV1j) form a
  coherent sprint that closes the F-UJI + Horizon Europe + EU AI Act
  per-artefact-visibility gap. Worth dispatching as a single FAIR-sprint
  bundle.
- All FAIR + MFG items target the same `AbstractDataObject` surface — touch
  the entity once, ship N fields in one migration to keep V## numbers tidy.
- The UX backlog (UX-*, PERF5–10) maps onto a single "MFFD-scale UI hardening"
  sprint; the survey already says "MFFD AFP TCP thermal trail dataset ETA
  2026-05-26" which is the perfect forcing function.
