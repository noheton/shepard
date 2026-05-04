# Dependency Report — shepard

Audit date: 2026-05-04. Manifests inspected:
- `backend/pom.xml`
- `clients/java/pom.xml` (parent only, no deps)
- `package.json` (root tooling)
- `frontend/package.json`
- `backend-client/package.json` (generated)
- `load-tests/package.json`
- `clients/tests/typescript/package.json`
- `scripts/pyproject.toml` (+ `scripts/poetry.lock`)
- `clients/tests/python/requirements.txt`
- `renovate.json`

No `go.mod`, `Gemfile`, or `Cargo.toml`.

## Top 10 priority upgrades

1. **Replace `dotenv` with `python-dotenv`** in `scripts/pyproject.toml` — confirmed in `scripts/poetry.lock`. The PyPI `dotenv` package is a low-quality orphan re-export owned by a single account; supply-chain risk. Effort XS.
2. **Verify `jinja2` lock ≥ 3.1.6** — `scripts/poetry.lock` resolves to 3.1.6 (good). Confirm in CI. CVE-2025-27516 (sandbox bypass), CVE-2024-22195/34064/56326 patched. Effort XS.
3. **Fix `renovate.json` typo `natchUpdateTypes`** (line 21) — silently breaks the intended automerge filter for the `ruff` rule. Effort XS.
4. **`jjwt` 0.11.5 → 0.12.x** — 0.11 branch is EOL; security patches now only land in 0.12+. Remove the `<0.12` Renovate pin. Effort S. Bundle with security finding H1 (JWT no-expiry).
5. **Nuxt 3.20.2 → Nuxt 4.x** — Nuxt 4 released 2025; plan migration. Effort L. Coordinate with active frontend feature work.
6. **ESLint 8 → 9** — 8.x is end-of-life; remove `eslint<9` Renovate pin and update ecosystem (`eslint-plugin-vue`, `@vue/eslint-config-*`). Effort M.
7. **JUnit Jupiter 5.10.5 → 5.12+** — relax `<5.11` Renovate pin; small upgrade. Effort XS.
8. **Quarkus 3.27.2 → latest LTS** — keep current with security patches. Effort S.
9. **Python `^3.11` → `^3.12`** in `scripts/pyproject.toml` — align with ruff's `py312` target. Effort XS.
10. **`@dlr-shepard/shepard-client` 5.1.2 → 5.2.0** in `clients/tests/typescript`; **`typescript` 5.5.3 → 5.6+** there. Sync with frontend & Python client. Effort XS.

Bonus cleanup: prune stale Vue 2-era Renovate pins (`vue<3`, `vuex<4`, `vue-router<4`, `portal-vue<3`, `typescript<5`, `neo4j-ogm<4`, `bootstrap`, `@vue/tsconfig<0.2`) — these no longer match real dependencies.

## Backend (Maven, Java 21, Quarkus)

| Package | Current | Latest | Severity | Notes |
|---|---|---|---|---|
| `io.quarkus.platform:quarkus-bom` | 3.27.2 | LTS or 3.30.x | low | Recent; Renovate restricts to LTS |
| `maven.compiler.release` | 21 | 21 LTS | none | Modern |
| `hibernate-spatial` | 7.2.6.Final | 7.2.x | none | Aligned with Quarkus 3.27 |
| `jjwt-api/impl/jackson` | 0.11.5 | 0.13.x | **medium** | EOL branch; remove `<0.12` Renovate pin |
| `neo4j-ogm-core / bolt-driver` | 5.0.3 | 5.0.x latest patch | low | Renovate `<4` pin is stale (already on 5) |
| `neo4j-cypher-dsl` | 2025.2.4 | 2025.x | none | Recent |
| `org.codehaus.janino:janino` | 3.1.12 | 3.1.x | none | Current |
| `junit-jupiter` | 5.10.5 | 5.12+ | low | Relax `<5.11` Renovate pin |
| `org.mockito:mockito-core` | 5.22.0 | 5.x | none | Modern |
| `org.projectlombok:lombok` | 1.18.44 | 1.18.x | none | Recent |
| `org.assertj:assertj-core` | 3.27.7 | 3.27.x | none | Current (recent SECURITY bump) |
| `nl.jqno.equalsverifier` | 4.4.1 | 4.x | none | Current |
| `com.opencsv:opencsv` | 5.12.0 | 5.x | none | Up to date |
| `commons-lang3` | 3.20.0 | 3.x | none | CVE-2025-48924 fixed in 3.18.0 — safe |
| `commons-io` | 2.21.0 | 2.21.x | none | CVE-2024-47554 fixed in 2.18.0 — safe |
| `org.jsoup:jsoup` | 1.22.1 | 1.20.x | low | CVE-2022-36033 fixed in 1.15.3 — safe |
| `eu.michael-simons.neo4j:neo4j-migrations` | 3.2.1 | 3.x (4.x via MR !810) | none | Recent |
| `edu.kit.datamanager:ro-crate-java` | 2.1.0 | 2.x | none | Current |
| `io.quarkiverse.wiremock:quarkus-wiremock-test` | 1.5.3 | 1.x | none | Current |
| `jakarta.servlet-api` | 6.1.0 | 6.1.x | none | Current |
| `jacoco-maven-plugin` | 0.8.14 | 0.8.x | none | Current |
| `spotbugs-maven-plugin` | 4.9.8.2 | 4.9.x | none | Configured but **not invoked in CI** — see security report |
| `findsecbugs-plugin` | 1.14.0 | 1.14.x | none | Same — not invoked in CI |
| `maven-pmd-plugin` | 3.28.0 | 3.28.x | none | Current |
| `versions-maven-plugin` | 2.17.1 | 2.17.x | none | Renovate pin `<2.18` |
| `license-maven-plugin` | 2.4.0 | 2.x | none | Renovate pin `<2.5` |

Transitive dependencies (Jackson, Netty, Logback, Hibernate, Resteasy) are pulled at versions managed by Quarkus BOM 3.27.2. No direct `log4j-core`, no Spring Boot, no `jackson-databind <2.15`. None of the historical critical CVEs (Log4Shell, Spring4Shell, Jackson 2.9.x) are reachable.

## Frontend (Nuxt 3 / Vue 3)

| Package | Current | Latest | Severity | Notes |
|---|---|---|---|---|
| `nuxt` | 3.20.2 | Nuxt 4.x | medium | Plan migration within 6-12 months |
| `vue` | 3.5.13 | 3.5.x | none | Recent |
| `vuetify` | 3.12.1 | 3.x | none | Recent |
| `@sidebase/nuxt-auth` | 0.10.1 | 0.10.x / 1.x | low | Pre-1.0 |
| `@nuxt/fonts` | 0.14.0 | 0.x | low | Pre-1.0 |
| `chart.js` | 4.5.1 | 4.x | none | Current |
| `highlight.js` | 11.11.1 | 11.x | none | Current |
| `@tiptap/*` | 3.20.0 | 3.x | none | Pinned consistently |
| `vite` | ^6.0.0 | 6.x (Vite 7 available) | low | Vite 7 in 2025 |
| `typescript` | ^5.6.2 | 5.6.x+ | none | Modern |
| `vue-tsc` | 2.2.12 | 2.x/3.x | low | Track Vue tooling |
| `eslint` (transitive) | restricted `<9` | 9.x | medium | Pin needs lifting; 8.x EOL |
| `@nuxt/eslint` | 1.15.2 | 1.x | none | Recent |
| `@types/node` | 24.11.0 | matches Node 24 | none | Modern |
| `vite-plugin-vuetify` | 2.1.3 | 2.x | none | Current |
| `sass / sass-loader` | ^1.81.0 / ^16.0.3 | current | none | Recent |

## Root tooling (`/package.json`)

| Package | Current | Severity | Notes |
|---|---|---|---|
| `@openapitools/openapi-generator-cli` | 2.19.1 | none | Current |
| `concurrently` | 9.1.2 | none | Current |
| `prettier` | 3.5.3 | none | Current |
| `prettier-plugin-java` | 2.6.7 | none | Current |
| `prettier-plugin-organize-imports` | 3.2.4 | low | Pinned `<4` (TS5 compat); revisit |
| `rimraf` | 6.0.1 | none | Current |

## Load tests (`/load-tests/package.json`)

All current: `@types/k6` 1.6.0, `core-js` 3.48, `webpack` 5.105, `webpack-cli` 6.0.1 (MR !806 wants v7), `ts-loader` 9.5.4, `typescript` 5.6.3, `rimraf` 6.1.3.

## clients/tests/typescript

| Package | Current | Severity | Notes |
|---|---|---|---|
| `@dlr-shepard/shepard-client` | 5.1.2 | low | Lagging Python client (5.2.0); align |
| `ts-node` | 10.9.2 | none | Current |
| `typescript` | 5.5.3 | low | Behind frontend's 5.6.x |

## Python (scripts + clients/tests/python)

| Package | Declared | Severity | Notes |
|---|---|---|---|
| Python | `^3.11` (declared); ruff target `py312` | low | Bump to `^3.12` for consistency |
| `python-gitlab` | 5.6.0 | none | Current |
| `ruff` | 0.15.4 | none | Modern; `renovate.json` typo silently disables automerge filter |
| `jinja2` | `^3.1.2` (lock 3.1.6) | none | All known 3.1.x CVEs patched in 3.1.6 |
| `click` | `^8.1.7` | none | Current |
| `ruamel-yaml` | `^0.19.0` | none | Current |
| `pandas` | `^2.3.3` | none | Current |
| `numpy` | `^2.3.5` | none | Modern (numpy 2) |
| **`dotenv`** | `^0.9.9` | **medium** | Replace with `python-dotenv`; supply-chain risk |
| `shepard-client` | 5.2.0 | none | In sync |

## Renovate config summary (`renovate.json`)

- Extends `config:recommended`, ignores `node_modules`/`bower_components`, runs config migration.
- Base branch: `develop`.
- OSV vulnerability alerts enabled, dependency dashboard enabled, label `dependencies`, minimum release age 14 days.
- Grouping: develop, frontend ("nuxtend"), backend, infrastructure, scripts, load-tests, openapi-generator, shepard-client.
- Quarkus updates grouped with custom PR body advising LTS-only.
- Many legacy `<` pins for npm Vue 2 ecosystem (likely obsolete) and Java pins (`jjwt<0.12`, `neo4j-ogm<4`, `junit-jupiter<5.11`).
- Image pins: `chronograf<1.10`, `neo4j<5`, `mongo<5`, `timescaledb` disabled.
- **Bug**: typo `natchUpdateTypes` (line 21) breaks the intended automerge filter for ruff.

## Open Renovate MRs (snapshot)

The following GitLab MRs are in flight (mostly automerge-pending; not bit-rotted):

`!636 develop`, `!647 openapi-generator-cli v7`, `!695 scripts (major)`, `!722 nuxtend (major)`, `!723 backend Quarkus`, `!734 python Docker tag v3.14.3`, `!740 timescaledb v2.26.3`, `!758 nuxtend`, `!766 infrastructure`, `!779 vuetify`, `!780 vue-tsc`, `!803 backend`, `!804 scripts`, `!805 load-tests`, `!806 webpack-cli v7`, `!810 neo4j-migrations v4`.

**Sequencing risk**: `!723` (Quarkus) collides with active backend refactor MRs `!808/!809` (#712); `!722/!758` (nuxtend) collide with planned frontend work for #720. Hold dependency MRs touching the same surface area until the feature MR lands.
