# Implementation Plan — shepard

Snapshot date: 2026-05-04. Phased plan derived from Wave 1-3 outputs and Wave 4 synthesis.

## Plan at a glance

| Phase | Focus | Effort total | Risk | Cluster refs |
|---|---|---|---|---|
| 0 | Housekeeping (mirror sync, file-as-issue, hygiene) | S-M | low | reconciliation, code-quality |
| 1 | Security fixes (critical → high → medium) | M-L | high reward | C1-C5, H1-H8, M1-M12 |
| 2 | First issues (XS / S, fresh) | S-M | low | A, F (small items), J (cleanup) |
| 3 | Core improvements (medium effort, high value) | L | medium | A epic, F epic, Subscription epic, FE foundation |
| 4 | Larger work (L / XL) | XL | high | E (Neo4j refactor), Search hardening |
| 5 | Dependency & code-quality cleanup | M | low | J Renovate, code-quality TODOs |
| 6 | Backlog / Won't-fix | XS (mostly closure) | low | I long-tail, G UX, K bit-rotted MR |

---

## Phase 0 — Housekeeping

**Goal**: get the GitHub mirror state honest, file untracked findings, fix small documentation/config drift.

### 0.1 — Mirror reconciliation
- Re-import GitLab issue **#557** (only open GitLab issue not on GitHub mirror).
- Re-enable MR mirroring (broken since 2024-09-16; ~23 open + 80-100 closed/merged MRs missing).
- Add confirmation comments to GH PRs #995, #994, #963, #946 pointing to authoritative GitLab MRs.
- Document gh#683 as a mirror artifact (orphan since GL #684 was deleted upstream).

### 0.2 — File untracked critical findings as GitHub issues
- C1 — SQL injection in `NativeQueryStringBuilder.java`
- C2 — CORS wildcard with state-changing methods
- C3 — Permissions fallback grants full access
- C4 — SSRF + ReDoS in subscription webhook filter
- C5 — Cypher injection in `Neo4jQueryBuilder.java`
- H1 through H8 (8 high findings)

(Per repository policy these go to **GitHub** copies, not GitLab.)

### 0.3 — File untracked code-quality items
- TODOs at `PermissionsIO.java:43`, `StructuredDataSearchService.java:87`, `SubscriptionFilter.java:62`, `DataObjectService.java:358`, `LabJournalEntryRest.java:41`, `ExportService.java:92`, `FileContainerServiceTest.java:309` ("doesn't test anything"), `StructuredDataContainerServiceTest.java:258`, `PermissionsServiceSecondTest.java:36`, `PKIHelperTest.java:22`.

### 0.4 — Trivial config / doc fixes
- Fix `renovate.json:21` typo `natchUpdateTypes` → `matchUpdateTypes`.
- Add ADR 019 + ADR 020 entries to `architecture/src/09_architecture_decisions/index.adoc`.
- Fix typo in active GitLab label `staus::longterm` → `status::longterm` (decision: re-label or accept).

**Effort total**: S-M. **Risk**: low. **Order**: do 0.1-0.4 in any order; can be one combined PR for 0.3-0.4.

---

## Phase 1 — Security fixes

**Goal**: close critical and high security findings; activate SAST so the next PR is checked automatically.

### Phase 1A — CRITICAL (do first)

| # | Finding | Files | Acceptance | Effort | Tests needed |
|---|---|---|---|---|---|
| C3 | Permissions fallback inversion | `PermissionsService.java:258-262` | `getRoles` for missing Permissions returns `Roles(false,false,false,false)`; startup audit fails deploy if any `BasicEntity` lacks `has_permissions` edge | S | per-entity-type integration test verifying Permissions node creation; negative-path tests for entities without Permissions |
| C5 | Cypher injection — parameterise search | `common/search/query/Neo4jQueryBuilder.java:200-202,239-241,280,376,386` | All user-controlled values bound as Cypher parameters; property names whitelisted to enum or `[A-Za-z_][A-Za-z0-9_]*` | L | search regression suite; adversarial property-name fixtures |
| C1 | SQL injection — parameterise spatial JSON queries | `data/spatialdata/repositories/NativeQueryStringBuilder.java:40,65,116-123`; `SpatialDataPointRepository.java:47-55,86-94` | Replace `'%s'` interpolation with named parameters and `CAST(:metadata AS JSONB)`; dynamic JSON paths via `jsonb_path_exists(:json, :path::jsonpath)` | M | adversarial JSON/SQL injection fixtures; spatial integration test (currently 0.26 ratio — invest before fixing) |
| C4 | Subscription SSRF + ReDoS | `common/filters/SubscriptionFilter.java:63-78` | Regex compile uses timeout-bounded matcher; `callbackURL` enforces HTTPS + denylist of loopback/link-local/RFC1918/cloud-metadata; redirects disabled; `EventIO` carries IDs only | M | URL fixtures; ReDoS benchmark; mock outbound webhook |
| C2 | CORS allowlist | `application.properties:18-21` | `quarkus.http.cors.origins=${SHEPARD_ALLOWED_ORIGINS}`; `access-control-allow-credentials=false`; per-deployment value | XS | configuration test |

**Effort total**: M-L. **Risk**: high reward, medium implementation risk on C5 (large search surface).

### Phase 1B — HIGH

| # | Finding | Files | Effort | Notes |
|---|---|---|---|---|
| H7 + H1 | jjwt 0.11 → 0.12.x **plus** add `setExpiration` to API-key JWTs | `pom.xml`; `auth/apikey/services/ApiKeyService.java:138-150`; `common/filters/JWTFilter.java` | S | Bundle in one MR; remove Renovate `<0.12` pin |
| H2 | `UserLastSeenCache` TTL: 30 min → 5 min (or update docs) | `auth/security/UserLastSeenCache.java:8` | XS | Decide: change code or update `auth.adoc` |
| H3 | OPTIONS bypass — subsumed by C2 | `JWTFilter.java:98-101` | XS | No standalone change once C2 is fixed |
| H4 | `ShepardExceptionMapper` sanitisation | `common/exceptions/ShepardExceptionMapper.java:21-24` | S | Generic 500 message; `WebApplicationException` subclasses keep their messages |
| H5 | `PublicEndpointRegistry` exact-match | `common/filters/PublicEndpointRegistry.java:8-15` | XS | Anchored match + URI normalisation + tests |
| H6 | Replace `dotenv` with `python-dotenv` | `scripts/pyproject.toml:18`; `scripts/poetry.lock` | XS | Smoke-test CLI |
| H8 | Default credentials | `application.properties:133-145`; `infrastructure/.env.example` | XS | Replace with `CHANGE_ME_<random>`; add startup check |

### Phase 1C — MEDIUM (M1-M12)

Fold into Phase 5 cleanup unless adjacent to a Phase 1 fix. Notable inline fixes during Phase 1:
- M2 — set `0600` on private key (`PKIHelper.java:107-110`).
- M4 — `header.substring(7)` instead of `replace("Bearer ", "")` (`JWTFilter.java:159`).
- M5 — redact tokens in failure logs (`JWTFilter.java:113-117`).
- M12 — **Activate SpotBugs / findsecbugs in CI** (`backend/pom.xml:476-490`). Move plugins from `<reporting>` to `<build>`; fail on Medium-severity security categories. **Highest leverage in Phase 1.**

### Phase 1D — CI security plumbing (Epic 7)
- Activate SpotBugs+findsecbugs in `<build>` (M12).
- Add Trivy container scan.
- Add `eslint-plugin-security` for frontend.
- Add `bandit` or `semgrep` for `scripts/`.
- Run full `mvn verify` (not just `check`) on main and release pipelines.
- Extend secret scanning to fail on default values like `shepard_secret`, `shepardshepard`.

**Phase 1 effort total**: M-L. **Phase 1 risks**: C5 has wide blast radius; must be a parameterisation patch only — do NOT conflate with Cluster E refactor.

---

## Phase 2 — First issues

**Goal**: ship small, fresh wins; confirm closures; set up frontend test foundation alongside the first frontend feature.

| # | Goal | Files | Acceptance | Effort | Tests | Deps |
|---|---|---|---|---|---|---|
| #710 | Verify-and-close | `TimeseriesReferenceRest.java:267-275` | issue closed, regression test exists | XS | one regression test if missing | none |
| #711 | Verify-and-close | `CsvFormat.java`, `CsvColumnLineProvider.java` | closed | XS | regression test for column mode | none |
| #721 | Fix race in default file container | `data/file/services/FileContainerService.java` | concurrent creation cannot produce duplicate `default` relations | S | concurrent integration test | none |
| #717 | Permission gate on timeseries metadata | `data/timeseries/services/TimeseriesService.java:80` | unprivileged users cannot read metadata; container-level permission no longer required for metadata-only path | S | negative-path tests | aligns with Phase 1 C3 |
| #667 | Recent permission bug | (per issue) | bug fix + regression test | S | per issue | aligns with Phase 1 C3 |
| !800 | Documentation update | `architecture/...`, `README.md` | merged | S | n/a | none |
| #722, #688, #683 | Fresh search/UX bugs | `common/search/...` | bug fixes | S each | regression tests in HIGH-risk module | none |
| (enabler) | Frontend Vitest scaffolding | `frontend/` | Vitest configured; `npm test` in CI; one component test passes | M | n/a | precedes #720 |
| #720 | Frontend column export | `frontend/components/container/timeseries/...` | UI exposes the column-CSV export the backend provides; component test for the page | M | new component test | needs Vitest scaffolding above |

**Phase 2 effort total**: S-M. **Phase 2 risks**: low — these are fresh, owner-tracked items.

---

## Phase 3 — Core improvements

**Goal**: ship medium-effort high-value workstreams sequenced via cluster map. Respect architecture boundaries.

### 3.1 — Permissions Hardening (Epic 2)
- C3 already shipped in Phase 1; ensure regression coverage.
- Triage and act on #41, #62, #424, #483 — close superseded; fix live ones.
- Tech-debt #1 (entity-creation invariant) — addressed by C3's startup audit.
- Tech-debt #2 (cryptic OIDC subject usernames) — design proposal for stable mapping.

**Effort**: M-L. **Risks**: needs explicit owner; F has been long-standing.

### 3.2 — Subscription Hardening (Epic 4)
- C4 already shipped in Phase 1; expand subscription test coverage; add per-user subscription cap (related to L4 informational finding).

**Effort**: S-M. **Risks**: low — module-isolated.

### 3.3 — Active timeseries cluster closure (Epic 1)
- Land !808 / !809 (#712 sub-tasks). Continue !763 (search annotatedTimeseries).
- Cut shepard 5.4.2 once Sprint 23 cluster is green.

**Effort**: L. **Risks**: collides with Renovate Quarkus !723 — freeze !723 until 5.4.2 ships.

### 3.4 — MEDIUM security findings (M1-M12 minus those already done in Phase 1)

| # | Notes |
|---|---|
| M1 | `quarkus.http.limits.max-body-size` |
| M3 | RSA-2048 → RSA-3072 / EdDSA |
| M6 | logging-filter scope |
| M7 | exception-mapper rate-limit |
| M8 | per-Service permission-check tests |
| M9 | bind `createdBy` / `updatedBy` as Cypher parameters |
| M10 | `Vary: Origin` on CORS responses |
| M11 | document required `FRONTEND_AUTH_SECRET` entropy |

**Effort**: M total. **Risks**: low.

---

## Phase 4 — Larger work

**Goal**: structural refactors that unblock entire clusters. Plan is approach + PR breakdown.

### 4.1 — Cluster E: Neo4j refactor (#274, #577, #660)

- **Approach**: ADR for the new entity model; shadow tables / dual-write migration; `neo4j-migrations` scripts; cutover; cleanup.
- **PR breakdown** (suggested):
  1. ADR-021 documenting the new model.
  2. New entity classes alongside old ones.
  3. Read path: prefer new model, fall back to old.
  4. Migration scripts + dual-write in writer services.
  5. Cutover and removal of legacy paths.
  6. Cleanup of deprecated MongoDB queries (`StructuredDataSearchService.java:87` TODO).
- **Open questions**:
  - Owner — currently no active assignee; without one, Phase 4 cannot start.
  - Backwards-compat for older Python clients (#664 closed but legacy concern).
  - Performance budget vs. current Neo4j query patterns.
- **Dependency risks**: blocks D (semantics) fully; partially blocks A long-term; affects every search query (cluster H).

### 4.2 — Cluster D: Semantic annotations (#43, #553, #656, #660)
- Sequenced **after** 4.1 lands (or at least after 4.1 stage 3 enables read-side parity).
- #43 already PARTIAL (`data/semantic/`); finish coverage across all entity types.

### 4.3 — Cluster B: Versioning frontend (#46, #127, #150, #155, #271)
- Gated on Epic 6 (Phase 2 enabler) being live.
- Owner-confirm scope; scope-reduce per `ready-to-close.md` recommendation.

### 4.4 — Cluster C: Spatial-data product decision (#441-#447, #530, #557)
- Product decision: graduate (remove feature flag) OR deprecate.
- C1 (SQL injection) is fixed in Phase 1 regardless.
- If graduate: implement #441-#447 / #530 / #557 with spatial test suite.
- If deprecate: bulk-close per `ready-to-close.md`.

**Phase 4 effort**: XL. **Phase 4 risks**: ownership in handover phase; do not start without an explicit assignee.

---

## Phase 5 — Dependency & code-quality cleanup

**Goal**: drain Renovate backlog without colliding with Phase 2-4 work; close the remaining code-quality TODOs.

### 5.1 — Renovate batch drain (Cluster J)

| Batch | MRs | When |
|---|---|---|
| Build tooling / non-frontend non-runtime | `!647`, `!695`, `!804`, `!805`, `!806`, `!810`, `!766`, `!734`, `!740` | At any time, after each green CI |
| Frontend (non-#720-colliding) | `!779`, `!780` | Before #720 work, or after |
| Frontend (Nuxt majors, #720-colliding) | `!722`, `!758` | After #720 lands |
| Backend Quarkus (A-cluster-colliding) | `!723` | After !808/!809 land and 5.4.2 ships |
| `develop` aggregator | `!636`, `!803` | After their constituent updates |

### 5.2 — Renovate config cleanup
- Done in Phase 0: typo fix.
- Prune stale Vue 2-era pins (`vue<3`, `vuex<4`, `vue-router<4`, `portal-vue<3`, `typescript<5`, `@vue/tsconfig<0.2`, `bootstrap<5`).
- Relax `jjwt<0.12` (after H7 lands).
- Relax `junit-jupiter<5.11`.
- Relax `neo4j-ogm<4`.

### 5.3 — Other dependency upgrades not yet covered by an MR
- Nuxt 3.20 → Nuxt 4 (medium effort; plan).
- ESLint 8 → 9 + Vue ecosystem (medium effort; plan).
- Vite 6 → 7 (small).
- Python `^3.11` → `^3.12` in `scripts/pyproject.toml`.
- `@dlr-shepard/shepard-client` 5.1.2 → 5.2.0 in `clients/tests/typescript`.
- `typescript` 5.5.3 → 5.6+ in `clients/tests/typescript`.

### 5.4 — Code-quality TODO closure
- Merge `PermissionsServiceSecondTest` into `PermissionsServiceTest`.
- Refactor `LabJournalEntryRest.java:41` endpoint logic into the service layer.
- Optimise `DataObjectService.java:358` `referencedIds` loop.
- Fix the two "doesn't test anything" tests at `FileContainerServiceTest.java:309` and `StructuredDataContainerServiceTest.java:258` (likely paired with Phase 2 #721).
- Improve `ExportService.java:92` strategy pattern.

**Phase 5 effort**: M. **Phase 5 risks**: low; sequencing with Phase 2-4 is the only concern.

---

## Phase 6 — Backlog / Won't fix

**Goal**: get the remaining 100+ open issues to a state where the next planning round can read them.

### 6.1 — UX cluster (#642-#645)
Bulk-close per `ready-to-close.md`. **Effort**: S (closure pass).

### 6.2 — Bit-rotted MR !498
Close (or reassign for rebase if anyone volunteers). **Effort**: XS.

### 6.3 — Long-tail research / refactoring (~70 items)
Bulk close-with-comment per `ready-to-close.md` template, excluding owner-assigned items. **Effort**: S (closure pass).

### 6.4 — Spatial cluster (if Phase 4 product decision = deprecate)
Bulk-close #441-#447 / #530 / #557. **Effort**: XS.

### 6.5 — Versioning low-value items
Close #150 / #155 / #271 unless Cluster B is greenlit in Phase 4. **Effort**: XS.

**Phase 6 effort**: S total. **Phase 6 risks**: requires maintainer pass; do not bulk-close without owner sign-off.

---

## Suggested order

```
Phase 0 (housekeeping)              — week 0
  ↓
Phase 1A (CRITICAL security)        — weeks 1-3
Phase 1B (HIGH security)            — weeks 1-3 (parallel)
Phase 1D (CI plumbing)              — week 1
  ↓
Phase 2 (first issues + Vitest)     — weeks 3-5
  ↓
Phase 3 (core improvements)         — weeks 5-9
  ↓
Phase 4 (larger work — needs owner) — months 3-6
  ↓
Phase 5 (dep + code-quality drain)  — continuous from week 4
  ↓
Phase 6 (backlog closure)           — weeks 1-2 maintainer pass
```

Phases 5 and 6 run continuously alongside the others; Phase 6 in particular should happen early to recover backlog signal.

## Key risks across phases

1. **Handover phase**: ownership for Phase 4 (Cluster E) and Phase 3.1 (Permissions epic) is not currently assigned. Without explicit assignment these slip.
2. **Renovate vs feature collisions**: !723 Quarkus must not land before !808/!809; !722/!758 nuxt should not land mid-#720.
3. **C5 patch must not be conflated with Cluster E** — patch first, refactor later.
4. **Frontend zero tests**: every Phase 2-4 frontend MR is regression-prone until Vitest scaffolding is live.
5. **`UserLastSeenCache` 30-min vs 5-min** — small fix but signals lack of Auth.Users tests; treat as a coverage signal during Phase 1B.
6. **Bulk closure (Phase 6)** — requires maintainer sign-off; cannot be done by an agent unilaterally.
