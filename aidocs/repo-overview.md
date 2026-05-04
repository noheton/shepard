# Repo Overview — shepard

GitHub: https://github.com/noheton/shepard (mirror)
GitLab (authoritative): https://gitlab.com/dlr-shepard/shepard
Snapshot date: 2026-05-04
Branch reviewed: `claude/cleanup-github-mirror-f2bsP` (from `develop` HEAD `d55b8de`).

## Project goals & scope

Shepard ("Storage for HEterogeneous Product And Research Data") is a multi-database research-data platform developed by DLR's Center for Lightweight Production Technology (Augsburg). It exposes a single REST API that stores timeseries, files, structured documents, spatial data, and semantic annotations alongside metadata, in support of FAIR research-data management.

Quality goals (`architecture/src/01_introduction_and_goals/index.adoc`), prioritized: Usability, Reliability, Maintainability, Performance, Operability.

Constraints (`architecture/src/02_architecture_constraints/index.adoc`):
- On-premises only (no cloud, no internet); operable in DLR environments.
- Open-source-licensed dependencies only.
- FAIR principles; backwards-compatible data migrations.
- Browser support: Firefox ESR + latest Edge; desktop/tablet only.

## Top-level layout

```
backend/              Quarkus 3.27.2 / Java 21 REST API
backend-client/       TypeScript client (auto-generated from OpenAPI)
clients/{java,python,typescript,tests}  Multi-language client generation + tests
frontend/             Nuxt 3.20.2 + Vue 3 + Vuetify 3 web UI
infrastructure/       Docker Compose production deployment (Caddy, Prometheus, Grafana)
infrastructure-local/ Local dev environment (Keycloak + DBs)
load-tests/           k6 (TypeScript) performance suite — not in CI
scripts/              Python CLI utilities (Poetry)
architecture/         AsciiDoc Arc42 architectural documentation
.gitlab/              Modular CI/CD definitions (~800 lines)
renovate.json         Dependency-update automation config
```

Key files:
- `.gitlab-ci.yml` — main CI orchestrator
- `backend/pom.xml`, `frontend/package.json`, `scripts/pyproject.toml`
- `CONTRIBUTING.md`, `README.md`, `CITATION.cff`, `codemeta.json`
- No `CHANGELOG` (history tracked via Git tags).

## Technology stack

| Area | Tech |
|---|---|
| Backend | Quarkus 3.27.2, Java 21, Maven; JJWT 0.11.5; Hibernate Spatial 7.2.6; Lombok 1.18.44 |
| Frontend | Nuxt 3.20.2, Vue 3.5.13, Vuetify 3.12.1, TipTap, Vite 6 |
| Persistence | Neo4j 5.24 (metadata graph), MongoDB 8.0 (files & structured data), PostgreSQL+TimescaleDB (timeseries), PostgreSQL+PostGIS (spatial, behind feature flag) |
| Auth | External OIDC (Keycloak typical); JWT bearer tokens; secondary `X-API-KEY` header for long-lived API keys |
| Test stack (backend) | JUnit 5, Mockito 5, WireMock, JaCoCo, `@QuarkusTest`/`@QuarkusIntegrationTest` |
| Test stack (frontend) | **None — zero tests** |
| Migrations | Flyway (Postgres), neo4j-migrations (Neo4j) |
| Tooling | OpenAPI Generator, Renovate bot, k6, Prettier, Ruff, docToolchain |

Build & entry points:
- Backend main: `backend/src/main/java/de/dlr/shepard/ShepardMain.java` → `mvn package -P prod -Drevision=${VERSION_NUMBER}`
- Frontend: `npm run dev | build | preview` (Nuxt)
- Clients: OpenAPI Generator
- Documentation: docToolchain → HTML

## Architecture

Backend modules (`architecture/src/05_building_block_view/index.adoc`):

- **Data**: Timeseries, Structured Data, Files, Semantic Repository, Spatial Data
- **Context**: Collections & DataObjects, Lab Journal, Export, References, Version
- **Auth**: API Keys, Permissions, Users, Security
- **Common**: Configuration, Exceptions, Filters, MongoDB, Neo4j, Search, Subscription, Healthz, Versionz, Util

Data model (per the wiki `Data-Model.md`): Collections → DataObjects (parent/child, predecessor/successor) → References (Structured-Data, File, Timeseries, Collection, DataObject, URI) → Containers (Timeseries, Structured Data, File).

Integration points:
- REST: `https://[host]/shepard/api/...`; OpenAPI at `/shepard/doc/openapi.json`; Swagger UI at `/shepard/doc/swagger-ui`.
- OIDC identity provider via JWT bearer tokens; static OIDC public-key pinning.
- Outbound webhook subscriptions (regex-matched URLs/methods) — see security finding C4.
- Outbound SPARQL queries to semantic repositories (e.g., Ontobee).
- RO-Crate ZIP export with `ro-crate-metadata.json`.
- Prometheus metrics at `/shepard/doc/metrics/prometheus`.

Architecture Decision Records (ADRs) of note (`architecture/src/09_architecture_decisions/`):

- ADR-002: Quarkus chosen over Spring Boot/Javalin/Micronaut.
- ADR-005: Nuxt + Vuetify frontend.
- ADR-008: Neo4j + MongoDB + Postgres (TimescaleDB/PostGIS) — MinIO/Postgres-only rejected (32 TB limit, migration cost).
- ADR-010/011: Postgres+TimescaleDB replaced InfluxDB for timeseries.
- ADR-014/017: PostGIS for spatial data (380 ms vs 59 s vs pgvector for bounding-box).
- ADR-019: Member injection preferred over constructor injection (flipped from ADR-004).
- ADR-020: Single relation for default container.

ADR index file `09_architecture_decisions/index.adoc` is **out of date** — it lists through 018 but ADRs 019 and 020 exist on disk.

## CI/CD baseline

Five pipeline shapes wired from `.gitlab-ci.yml`:

1. **Feature branch**: secret-detection, dependency-scanning, prettier, scripts check.
2. **Merge request**: check + build (backend, frontend, clients) + test (unit + integration + migrations + coverage) + diff jobs.
3. **Develop**: same as MR + upload Docker images & client packages tagged `:dev`.
4. **Main**: only check stage (no build/test).
5. **Release tag** (`*-release` regex): full pipeline with Docker registry & Maven/PyPI/npm registry uploads.

In-CI database services for tests: Neo4j 5.24, MongoDB 8.0, Postgres 16+TimescaleDB 2.18, PostGIS 3.5.

### CI/CD gaps

| Gap | Impact |
|---|---|
| **No active SAST** — SpotBugs + findsecbugs are configured under `<reporting>` but never run during `mvn verify` or CI | Security findings missed at PR time |
| **No container image scanning** (Trivy etc.) | Base-image CVEs missed |
| **No Java linting** (CheckStyle/PMD/SpotBugs) in pipeline | Code-style drift |
| **No frontend tests at all** | Every frontend change is a regression risk |
| **No E2E tests** (frontend ↔ backend) | Integration drift |
| **No load tests in pipeline** (k6 suite present but unused) | Performance regressions invisible |
| **Main branch pipeline minimal** (no build/test) | Release tags only validated via tag pipeline |
| **Coverage report MR-only** | Main coverage not tracked |
| **Prettier check-only** | No auto-formatting |

## Documentation inventory

| Document | Location | Format | Status |
|---|---|---|---|
| README | `README.md` | Markdown | Quick setup + links |
| Contributing | `CONTRIBUTING.md` | Markdown | Branching, dev setup, review checklist |
| Architecture (Arc42) | `architecture/src/{01..12}_*/index.adoc` | AsciiDoc | Comprehensive but several stub chapters |
| ADRs | `architecture/src/09_architecture_decisions/{000..020}-*.adoc` | AsciiDoc | Index out of date (019, 020 missing from index) |
| Codemeta | `codemeta.json` | JSON-LD | Software metadata (ORCID authors, Zenodo) |
| Citation | `CITATION.cff` | CITATION format | Citation metadata |
| API docs | dynamic | OpenAPI 3.0 | Served by backend |
| Infrastructure | `infrastructure/README.md` | Markdown | Docker Compose deployment |

Stub / "TBD" chapters in architecture: `06_runtime_view`, `07_deployment_view`, `10_quality_requirements`, the Risks table.

## Wiki vs `/architecture` — gaps & contradictions

**Only in the wiki** (https://gitlab.com/dlr-shepard/shepard/-/wikis/home):
- Practical Python-client examples (`Examples/01-08`).
- DLR institutional context.
- Wiki-side data-model overview.

**Only in `/architecture`**:
- All ADRs + tech-choice rationale.
- Module-level building-block decomposition.
- Migration plan InfluxDB → TimescaleDB.
- Lab Journal feature.
- Spatial Data feature.
- Versioning / HEAD/UUID semantics.
- Validation, exception hierarchy, OpenAPI quirks.
- Testing strategy, k6 load tests, Renovate workflow, release/hotfix scripts.
- Quality goals, technical debt log, glossary.
- LastSeenCache, JWTFilter, UserFilter, PublicEndpointRegistry implementation details.

**Contradictions / inconsistencies**:
- Wiki `home.md` references a `[Backend]` page that does not exist (dead link).
- Wiki `Examples/05-Permissions.md` describes the permission model without the `PublicReadable` value (only listed in wiki `REST-API.md`); architecture (`auth.adoc`) authoritatively defines `Public`, `PublicReadable`, `Private`.
- Architecture says "5-minute grace period" for revoked API keys / permissions; the actual `UserLastSeenCache` TTL is **30 minutes** (`UserLastSeenCache.java:8`). See security finding H2.
- ADR-003 keeps frontend/backend as separate Docker images; wiki `home.md` mentions "two main parts" but admins must read `infrastructure/README.md` separately.

## Project state observations

- **Handover / wind-down phase** signals: active milestones include `Handover period` and `Interim`; multiple `xit-*` external-contractor assignees; many `staus::longterm` (sic) and `stale` labels.
- 166 open issues vs 35 fresh (21%) — large stale backlog.
- 23 open MRs are mostly Renovate dependency updates; only `!498` is bit-rotted (402 days idle).
- Active feature work concentrated in `shepard 5.4.2` milestone (timeseries refactor) and `Sprint 23` (issues #712-#716).
- Renovate is heavily used (205 MRs ever labeled `dependencies` ≈ 26% of all MRs).
- Typo in active label `staus::longterm` (vs intended `status::longterm`).
