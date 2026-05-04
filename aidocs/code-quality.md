# Code Quality — shepard

Snapshot date: 2026-05-04. Scope: `/home/user/shepard` at branch `claude/cleanup-github-mirror-f2bsP`.

## Summary signals

Positive:
- Lombok consistently used (reduced boilerplate).
- Modern testing stack (JUnit 5, Mockito 5, WireMock, JaCoCo).
- Prettier formatting (JS/TS/YAML) and Ruff linting on Python scripts enforced in CI.
- AsciiDoc Arc42 architectural documentation (well-structured even where chapters are stubs).
- Renovate bot drives dependency hygiene with grouping and minimum release age 14 days.

Concerns:
- No Java linting in CI (CheckStyle / PMD / SpotBugs not invoked).
- SpotBugs + findsecbugs configured but only in `<reporting>` (`backend/pom.xml:476-490`) — never run by `mvn verify`.
- No frontend tests at all (Nuxt 3 app, zero `*.spec.*` / `*.test.*`).
- ADR index file `architecture/src/09_architecture_decisions/index.adoc` does not include ADRs 019 and 020 (which exist on disk).
- Architecture chapters that are TBD/stubs: `06_runtime_view`, `07_deployment_view`, the Solution Strategy quality table, `10_quality_requirements`, the Risks table.
- Active label typo `staus::longterm` (vs intended `status::longterm`) is in use across multiple GitLab issues.

## TODO / FIXME / HACK / XXX comments

Total: **10**, all in backend Java; no TypeScript / Python technical-debt markers found.

| File:line | Category | Note |
|---|---|---|
| `backend/src/test/java/de/dlr/shepard/auth/permission/services/PermissionsServiceSecondTest.java:36` | test consolidation | Merge with existing `PermissionsServiceTest` |
| `backend/src/test/java/de/dlr/shepard/common/util/PKIHelperTest.java:22` | test methodology | "How to test filesystem interactions?" |
| `backend/src/test/java/de/dlr/shepard/data/file/services/FileContainerServiceTest.java:309` | coverage gap | "Unit test doesn't test anything; needs partial mocking" |
| `backend/src/test/java/de/dlr/shepard/data/structureddata/services/StructuredDataContainerServiceTest.java:258` | coverage gap | "Test doesn't properly test anything" |
| `backend/src/main/java/de/dlr/shepard/auth/permission/io/PermissionsIO.java:43` | versioning/architecture | "Multiple entities post versioning (design issue)" |
| `backend/src/main/java/de/dlr/shepard/data/structureddata/services/StructuredDataSearchService.java:87` | tech debt / migration | Deprecate MongoDB queries |
| `backend/src/main/java/de/dlr/shepard/common/filters/SubscriptionFilter.java:62` | performance | "This could become a bottleneck" — see security finding C4 |
| `backend/src/main/java/de/dlr/shepard/context/collection/services/DataObjectService.java:358` | performance | Inefficient loop generating `referencedIds` |
| `backend/src/main/java/de/dlr/shepard/context/labjournal/endpoints/LabJournalEntryRest.java:41` | code organization | Refactor endpoint logic to service layer |
| `backend/src/main/java/de/dlr/shepard/context/export/services/ExportService.java:92` | feature/design | "Add more export types, improve strategy pattern" |

None of these reference an issue number; they are not visibly tracked in GitLab.

## Test coverage map

Backend: 315 production files, 232 test files (0.74 ratio overall), 183 unit tests + 42 integration tests.

| Module | Prod | Tests | Ratio | Risk |
|---|---|---|---|---|
| **Data** |
| timeseries | 30 | 11 | 0.37 | **HIGH** |
| structureddata | 14 | 11 | 0.79 | MEDIUM |
| spatialdata | 27 | 7 | 0.26 | **HIGH** |
| file | 13 | 12 | 0.92 | LOW |
| **Context** |
| references | 47 | 37 | 0.79 | MEDIUM |
| semantic | 22 | 13 | 0.59 | MEDIUM |
| collection | 12 | 13 | 1.08 | LOW |
| version | 7 | 10 | 1.43 | LOW |
| export | 3 | 3 | 1.00 | LOW |
| labJournal | 5 | 1 | 0.20 | **HIGH** |
| **Auth** |
| apikey | 6 | 5 | 0.83 | LOW |
| permission | 5 | 6 | 1.20 | LOW |
| users | 11 | 9 | 0.82 | LOW |
| security | 10 | 4 | 0.40 | MEDIUM |
| **Common** |
| neo4j | 24 | 7 | 0.29 | **HIGH** |
| search | 37 | 14 | 0.38 | **HIGH** |
| subscription | 6 | 5 | 0.83 | LOW |
| exceptions | 10 | 8 | 0.80 | LOW |
| filters | 9 | 3 | 0.33 | MEDIUM |
| mongoDB | 3 | 3 | 1.00 | LOW |
| util | 17 | 9 | 0.53 | MEDIUM |

### High-risk modules — gap detail

- **timeseries (0.37)** — `TimeseriesDataPointRepository` and `TimeseriesRepository` have **no test classes**. Aggregation, grouping, fill-option logic untested in isolation. Active feature work (Sprint 23 #712-#716) lands in this module.
- **spatialdata (0.26)** — `SpatialDataPointRest` has no test class; geometric models (`BoundingSphere`, `OrientedBoundingBox`, `KNearestNeighbor`) untested. Houses security finding C1 (SQL injection).
- **neo4j (0.29)** — Core graph layer. `GenericDAO` has basic tests; relationship mapping and transaction handling lack depth.
- **search (0.38)** — Houses security finding C5 (Cypher injection). Edge cases in query validation, semantic search, and Neo4jQueryBuilder are thinly covered.
- **labJournal (0.20)** — Only `LabJournalTest.java` exists; no dedicated service tests.

### Frontend tests

**None.** No `*.spec.*` or `*.test.*` files anywhere under `frontend/`. No vitest / Jest / Playwright configuration. Zero unit, component, or e2e tests for a substantial Nuxt 3 + Vuetify app.

### Load tests

`load-tests/` contains 16 TypeScript k6 files. **Not integrated into CI** — runs manually only.

### Scripts

`scripts/` is a Poetry project with **no test files** (Ruff lint only).

## Top 5 places to invest in tests

1. **Timeseries repositories** — add `TimeseriesRepositoryTest` and `TimeseriesDataPointRepositoryTest`. Cover pagination, aggregation queries, time-range filtering. Aligns with Sprint 23 work.
2. **Spatial REST endpoint** — add `SpatialDataPointRestTest` covering CRUD + query-param parsing. Mandatory before fixing security finding C1 (SQL injection) so behavior can be locked down.
3. **Search query builders** — Neo4j and MongoDB query-builder edge cases. Required before / alongside security finding C5 (Cypher injection) fix.
4. **LabJournal** — add `LabJournalServiceTest` and integration tests for end-to-end workflows.
5. **Frontend Vitest scaffolding** — set up Vitest + Vue Test Utils; first targets: components touched by issue #720 (frontend column export) so the new feature can land alongside its tests, not after.

## Code-organisation observations

- `architecture/src/09_architecture_decisions/index.adoc` does not include ADRs 019 and 020. Minor doc bug.
- `LabJournalEntryRest.java:41` flags endpoint logic that should move to the service layer — code-organisation drift.
- `DataObjectService.java:358` flags an inefficient loop in `referencedIds` generation — performance smell flagged but unfiled.
- `StructuredDataSearchService.java:87` flags MongoDB query deprecation — connects to the long-term Neo4j refactor cluster (#274, #577, #660).
- Duplicate test class `PermissionsServiceSecondTest` should be merged into `PermissionsServiceTest`.

## Cross-reference with open issues

| Code-quality item | Related open issue | Action |
|---|---|---|
| `SubscriptionFilter.java:62` "could become a bottleneck" | (untracked) — also security C4 | File issue; bundle with C4 fix |
| `DataObjectService.java:358` inefficient loop | (untracked) | Low-priority perf issue |
| `LabJournalEntryRest.java:41` move logic to service | (untracked) | Refactor candidate |
| `ExportService.java:92` strategy pattern | possibly relates to general export work | Triage |
| `PermissionsIO.java:43` versioning/permissions | overlaps with versioning cluster (#46/#127/#150/#155/#271) and permissions cluster (#41/#62/#424/#483/#667) | Defer until Permissions Hardening epic |
| `StructuredDataSearchService.java:87` deprecate MongoDB queries | overlaps with Neo4j refactor cluster (#274/#577/#660) | Defer / fold into refactor |
| `FileContainerServiceTest.java:309` "doesn't test anything" | (untracked) | Test debt; pair with #721 race-condition fix |
| `StructuredDataContainerServiceTest.java:258` "doesn't test anything" | (untracked) | Test debt |
| `PermissionsServiceSecondTest.java:36` merge tests | (untracked) | Cleanup |
| `PKIHelperTest.java:22` test filesystem | overlaps with security M2 (`0600` perms) | Bundle |
