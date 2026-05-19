# Case Study: One Month of AI-Assisted Fork Development
**Project:** Shepard Research Data Management Platform (DLR fork)  
**Period:** ~2026-05-04 to 2026-05-19  
**Collaborators:** Flo Researcher (project lead), Claude (Anthropic, Sonnet 4.6 / Opus 4.7)  
**Audience:** Researchers, funders, engineering team, and the analysis agent reading this document

---

## 1. Background

Upstream shepard (`gitlab.com/dlr-shepard/shepard`, tag 5.2.0) is a solid research data management platform built at DLR. It manages Collections, DataObjects, and Containers (Timeseries, File, Structured Data) in a Neo4j graph, with a Quarkus Java backend and a Nuxt 3 frontend.

The fork started from two observations:
1. The upstream API (integer Neo4j IDs, monolithic `/shepard/api/` namespace) was showing age and had known friction points for external clients.
2. The research group had specific needs the upstream hadn't prioritised: richer user profiles, provenance tracing, timeseries charting, IDTA AAS interoperability, and a better demo experience.

The goal was to evolve the platform meaningfully without abandoning upstream compatibility — every change must leave the upstream API surface intact so existing clients keep working.

---

## 2. Interaction Log (Backfilled Chronology)

### Phase 1 — Architecture and Design (early May)
**~2026-05-04 to 05-07**

The work started with design docs, not code. The collaboration opened with an audit of the upstream API surface: API critique (`aidocs/23`), permission system review (`aidocs/24`), CRUD consistency table (`aidocs/26`), and a Neo4j ID migration design (`aidocs/25` — the L2a-L2e roadmap that led to `appId`).

This phase produced ~126 commits, almost all documentation. The human set the direction; Claude drafted the design docs, cross-referenced them, and built the backlog items that would gate future engineering. Notable: the L2 design (`aidocs/platform/25`) formalised the fork's identity — stable `appId` UUIDs for all entities, the `/v2/` API shelf, and the upgrade-path guarantees now encoded in `aidocs/34`.

**Process note:** Document-first worked well here. Having a written design before touching code meant that when the implementation came, the trade-offs were already settled. Claude's role in this phase was primarily synthesising requirements into coherent docs that the human could redirect.

---

### Phase 2 — Backend Feature Sprint (May 7–17)
**~340 commits over 10 days**

A long sequential sprint. Each session followed a pattern:
1. Human describes what's needed ("build the Unhide harvest plugin")
2. Claude explores the codebase, reads the relevant design doc, asks one clarifying question
3. Claude implements, builds, deploys, and runs smoke tests
4. Human reviews the running system in the browser, gives feedback
5. Next feature

**Features shipped in this phase (selected):**

| Feature | What it does |
|---------|-------------|
| L2d Phase A/A.2 | `/v2/collections` and `/v2/data-objects` CRUD with stable `appId` path params |
| WATCH1 | Collection → Container watch links (backend + frontend) |
| PROV1a | ProvenanceCapture filter — automatic `:Activity` nodes on every mutating request |
| P10b/c | SQL endpoint over TimescaleDB with CSV/NDJSON negotiation + admin config |
| P16 | `shepard-py` convenience Python client |
| AAS1a/b/d | IDTA AAS v3 Shell listing, submodel resolution, bundled template import |
| UH1a/d | Helmholtz Unhide harvest plugin with per-Collection publish toggle |
| KIP1c | ePIC handle minter plugin |
| F3 | Permission audit log (Postgres + admin REST) |
| N1e | Semantic term autocomplete in annotation picker |
| ROR1 | Research Organisation Registry integration + admin CLI |
| TM1a | Experiment-relative timestamp mode on Timeseries references |
| P23 | Pre-sign/cache TTL invariant validator |
| AI1c | Channel quality scoring on Timeseries references |
| TS_CHART_VIEW1 | Curated channel view persisted per container |
| G1d | Git reference check-update endpoint |
| INST1 | Admin-configurable instance branding |

Also in this phase: a complete migration to the `aidocs/` folder structure (8 subdirectories, 65+ design documents), with Claude maintaining the index and cross-referencing every new doc.

**Worktree pattern:** Claude spawned sub-agents via git worktrees for independent parallel tasks. This worked well for 2-3 parallel feature branches but broke down when more than ~5 agents ran simultaneously — 33 locked worktrees from a dead session on May 17 had to be salvaged and pruned. Lesson: limit parallelism to what can be meaningfully reviewed.

---

### Phase 3 — UI Modernisation (May 17–18)
**~100 commits over 2 days**

The frontend received a large modernisation pass:
- Landing page rebuilt action-first
- Empty-state CTAs on Collections and Containers list pages
- Sentence-case buttons globally
- Collapsible panels open by default (reducing friction for new users)
- Collection page: flat DataObjects panel for navigation at scale
- In-line Timeseries chart on the DataObject reference
- References summary chips
- Live mode for the channel chart (first version — flicker issue, later refined)
- Anomaly detection UI exposed in basic mode
- ORCID badge on user avatar
- "New DataObject" button on Collection page
- Stale-bundle banner (prompts reload when backend rolls)
- Inline markdown rendering for descriptions

The human's feedback loop here was tight — screenshot → fix → redeploy → next. Claude learned to not claim "done" without a deployed image on the public hostname. (See `feedback_deployed_before_asking.md` in memory.)

---

### Phase 4 — Profile, Auth, and Infrastructure (May 18–19)
**~30 commits**

A cluster of interconnected features that each required multiple debugging rounds:

**Avatar upload and display** — Three separate root causes before working end-to-end:
1. CORS: `allowCredentials` + specific origin echo needed for Bearer tokens
2. `appId` missing from `UserIO` — the `<img src>` was requesting the correct URL but the URL was never constructed (null appId)
3. JAX-RS route conflict: avatar PUT and GET on the same path without correct `@Consumes`/`@Produces` split
4. Stale backend image: the fix was committed but the image hadn't been rebuilt

**ORCID auto-sync** — Keycloak doesn't expose user attributes as OIDC claims by default; requires a "User Attribute" protocol mapper. Applied live via the Keycloak admin API and added to the realm JSON.

**Advanced mode toggle** — The backend was always returning 200. The bug was in the frontend API client: `MeApi.patchPreferences()` was pre-stringifying the body before passing it to `BaseAPI.request()`, which then ran `JSON.stringify()` again because `application/merge-patch+json` matches the `+json` MIME regex. The backend received a JSON string instead of an object → 400 → error toast.

**Per-shelf OpenAPI** — `OpenApiDocument.INSTANCE` is a static singleton that is cleared post-build in Quarkus native/JVM; it's always null at runtime. Fix: in-process loopback HTTP call to the SmallRye-served combined `/shepard/doc/openapi.json` + Jackson JSON-tree filtering by path prefix.

**Cache-control** — Browsers were caching stale HTML + JS bundles. Fix: Nuxt `routeRules` in `nuxt.config.ts` (not Caddy). HTML gets `no-store, must-revalidate`; hashed `/_nuxt/` assets get `immutable`.

**CC1b "Referenced by"** — All three container DAOs (Timeseries, StructuredData, File) had the wrong Cypher edge type. The relationship from Reference to Container is `[:is_in_container]`, not `[:has_payload]`.

---

## 3. Productivity Analysis

### Commits: upstream vs fork
- **Upstream (2021-07 to 2026-04):** ~350 commits over ~5 years
- **Fork (May 2026):** ~496 commits in ~16 days (2026-05-04 to 2026-05-19)

That's a ~3.5× acceleration on raw commit volume per calendar month. The comparison is imperfect (upstream had multiple maintainers, different priorities, slower review cycles), but directionally useful.

### Feature throughput (May 2026)
496 commits covering roughly 25 distinct backend features, a full frontend modernisation pass, 3 plugin modules, 98 design documents, a CI/CD pipeline rebuild, and multiple rounds of bug fixing — all in under three weeks with one human and one AI.

A conventional 2-engineer team in this codebase (Quarkus + Neo4j OGM + Nuxt 3) would realistically ship 3-5 features per engineer per sprint (2 weeks). This fork shipped ~25 features in roughly the same window — approximately 5–6× throughput against a 2-person baseline.

### Where the multiplier came from
1. **No context-switching cost for exploration.** Grep, read, and cross-reference happen in seconds. A human engineer reading a new codebase pays 30-60 minutes per new module; Claude can survey a 50-file subsystem in under a minute.
2. **No friction on boilerplate.** Cypher migrations, Jackson IO classes, JAX-RS annotations, Vue composables — Claude generates these at near-conversation speed. The human reviews, not writes.
3. **Design iteration at text speed.** A design doc that would take 4 hours to draft in isolation (deciding field names, upgrade paths, migration strategies) took 20-40 minutes as a collaborative dialogue. The human directed; Claude synthesised.
4. **Persistent context across tasks.** The memory system (`/root/.claude/projects/-opt-shepard/memory/`) accumulated project-specific knowledge (API version policy, deploy rules, feedback preferences) that carried between sessions.

### What did NOT speed up
- **Debugging root causes with invisible state.** The avatar/advanced-mode bugs each had 3-4 wrong hypotheses before the actual cause. Claude would fix one layer, the human would report "still broken", Claude would find the next layer. Multi-layer bugs in deployed systems are still slow — the feedback loop is "deploy → wait for human → try again."
- **Playwright in headless mode for Vuetify-heavy UIs.** Vuetify v3 components don't always expose semantic roles (`role="switch"`) and the visual tree is class-based. Several e2e tests required multiple iterations to find stable selectors.
- **Large parallel worktree sessions.** 33 locked worktrees after a dead agent session was a recovery cost. Parallelism has to be bounded or the cleanup exceeds the savings.

---

## 4. Risks and Mitigations

### R1 — Upgrade path maintenance
**Risk:** Adding 1163 commits of features on top of a 5.2.0 upstream fork creates a growing divergence. Future upstream releases become harder to integrate.  
**Mitigation:** `aidocs/34-upstream-upgrade-path.md` is the authoritative ledger — every schema change, new config key, and new endpoint is tracked with its migration path. `CLAUDE.md` enforces this as a non-optional rule. The `/v2/` API shelf is additive-only; the upstream `/shepard/api/` surface is frozen.  
**Residual risk:** The ledger can go stale if a session skips the update. The memory rule (`feedback_repo_maintenance.md`) and `CLAUDE.md` both enforce it, but they rely on the AI being in the loop. A human-only commit can drift without the tracker update.

### R2 — Test coverage trailing new code
**Risk:** Fast feature shipping with high commit velocity can outrun test coverage. The JaCoCo gate (≥60% overall, ≥70% per new file) catches regressions but doesn't guarantee behaviour coverage for new features.  
**Mitigation:** Coverage gate is enforced in CI (`backend-ci.yml`). New feature PRs are supposed to include tests.  
**Residual risk:** `VideoStreamReferenceServiceTest` is currently broken (storage layer refactor changed the API; tests weren't updated). There are similar ticking clocks in other areas where the production code moved but the test didn't. Periodic `mvn verify` runs catch these before they accumulate.

### R3 — Hand-maintained backend-client
**Risk:** `backend-client/src/apis/MeApi.ts` is hand-written and duplicates serialisation logic that the generated client handles differently. The double-encoding bug (fixed 2026-05-19) is an example of the divergence.  
**Mitigation:** The long-term fix is Kiota codegen (`clients-kiota.yml`). Until then, the rule is: pass raw objects to `BaseAPI.request()`, never pre-stringify for PATCH/POST.  
**Residual risk:** Other hand-maintained API methods may have the same bug latently.

### R4 — Deploy-before-feedback rule reliance
**Risk:** The "always deploy and verify on the public hostname before claiming done" rule requires a working deploy pipeline. If the pipeline breaks silently, the human could be testing a stale image.  
**Mitigation:** The image timestamp check (`docker inspect --format '{{.Created}}'` vs `git log -1 --format=%cI`) guards against stale images.  
**Residual risk:** A broken deploy that appears to succeed (container starts but serves old code) can still fool both human and AI.

### R5 — AI confidence on unfamiliar subsystems
**Risk:** Claude will give a confident wrong answer on a subsystem it hasn't explored. The early `has_payload` bug in the CC1b queries is an example — the comment in the code said the wrong thing, and the initial implementation trusted the comment without verifying the OGM model.  
**Mitigation:** The advisor tool (the stronger reviewer model) is called before committing to an approach and before declaring done. It has caught several cases where the initial approach was wrong.  
**Residual risk:** The advisor tool can miss things too, especially when the evidence is in deployed state rather than in the codebase. Human validation on the running system remains necessary.

### R6 — Context window and session continuity
**Risk:** Long sessions compress context, losing earlier decisions. A new session starts cold and can re-introduce removed code or contradict a settled design decision.  
**Mitigation:** The memory system (`/root/.claude/projects/-opt-shepard/memory/`) captures durable decisions. `CLAUDE.md` codifies the non-negotiable rules. Handover documents (like `aidocs/handover-2026-05-19.md`) bridge sessions.  
**Residual risk:** Memory entries can go stale. A rule that was right for a past version of the codebase can be wrong after a refactor.

---

## 5. Process Description (from Claude's perspective)

The collaboration pattern that emerged after the first week:

**1. The human defines a direction, not a spec.**  
Not "implement `GET /v2/collections/{appId}/data-objects` with these exact fields" but "we need a flat view of what's in a collection for navigation at scale." Claude fills the spec from the codebase context.

**2. Claude orients first, acts second.**  
Before writing any code, read the relevant design doc, grep for the existing pattern (there's almost always a parallel already in the codebase), check `aidocs/34` for upgrade obligations. This step is not optional even when the task seems small. The double-encoding bug and the `has_payload` bug both had obvious fixes visible in the code — but both were caused by someone (or some model) acting before orienting.

**3. Deploys are checkpoints, not the end.**  
After deploying, wait for the human to use the feature in the browser. Do not declare done. The error toast, the blank panel, the 0 in "Referenced by" — none of these show up in a grep or a unit test.

**4. Bugs require hypotheses, not guesses.**  
When something doesn't work, form a falsifiable hypothesis (e.g. "the backend is returning 400 because the body is double-encoded") and verify it directly (read the runtime.ts serialisation path, trace the content-type regex) before deploying a fix. Guessing and redeploying 4 times is slower than taking 5 minutes to read the code.

**5. Memory is for patterns, not events.**  
The `feedback_*.md` files capture rules that apply to future sessions ("never deploy to frontend without rebuilding backend-client/dist/"). The `project_*.md` files capture decisions ("basic mode = containerless, not a toggle"). What doesn't belong in memory: "today we fixed the avatar bug" — that's in git.

**6. The advisor is not a rubber stamp.**  
The advisor tool (stronger model reviewing the full conversation) has pushed back on approaches three times: once on the test verification strategy (pointed out the e2e test bypassed the API client), once on the context window management (recommended against parallel large-doc writes), once on the OpenAPI fix (the initial approach used a private SmallRye API that would break). Those pushbacks saved deploy cycles.

---

## 6. What This Looks Like as a Working Method

The pattern is not "human describes feature, Claude implements it." It's closer to:

> **Human as product owner + reviewer.** Sets direction, validates in the running system, decides trade-offs when two paths are equally valid. Spends most time looking at the live product.
>
> **Claude as senior engineer + document author.** Maintains code quality, enforces the architecture rules from CLAUDE.md, writes design docs, tracks open obligations, builds and deploys. Spends most cycles in the codebase.

The human's effective cognitive load in a typical session: 3-5 feedback messages ("ORCID still not set", "avatar now there, advanced mode still broken", "referenced by is still zero"). Claude resolves each one with a diagnosis, fix, and deploy cycle.

This is only sustainable because:
1. The architecture rules (upgrade-path tracking, API version policy, test floor) are written down and enforced by the model.
2. The deploy pipeline is reliable enough that a build-test-deploy cycle takes 3-5 minutes.
3. The human catches the "feels wrong" issues that don't show up as test failures (a blank panel, a toast where there should be a save, a count of 0 that should be non-zero).

---

## 7. Metrics Summary

| Metric | Value |
|--------|-------|
| Total commits (fork work, May 2026) | ~510 |
| Fork start | 2026-05-04 |
| Peak day | 2026-05-17: 107 commits |
| Distinct backend features shipped | ~28 |
| Plugin modules created | 3 (Unhide, ePIC minter, shepard-py) |
| Design documents written | 100+ |
| CI pipelines created or rewritten | 6 |
| Frontend Vitest unit tests added | 36 |
| Test files added/modified | ~45 |
| Bugs fixed in deployed system (May 18-19 alone) | 8 |
| Deploy cycles (May 18-19 alone) | ~15 |

---

## 8. Honest Assessment

**What worked better than expected:**
- Architecture documentation at speed. The aidocs/ corpus would have taken a team weeks; it emerged over days.
- Cross-subsystem consistency. Claude can grep the whole codebase for the pattern before implementing — so the 10th endpoint follows the same conventions as the 1st.
- Debugging from code rather than from logs. Most bugs were resolved by reading the source carefully, not by adding print statements and waiting.

**What was harder than expected:**
- Multi-layer bugs in live systems. Each "still not working" message represents a layer the current model didn't reach in the previous round.
- Playwright for Vuetify-heavy UIs. The Vuetify component tree doesn't match what accessible tooling expects. Several tests are weaker than they should be.
- Keeping documentation current. Every feature added 2-3 document obligations (aidocs/34, aidocs/42, aidocs/44, user docs). These were honoured, but the overhead is real.

**What would make this pattern more robust:**
- A staging environment with automatic deployment on every commit, so "is it deployed?" is never ambiguous.
- A lightweight integration test suite that runs against the live container on every deploy (not just Playwright e2e — actual API-level assertions with known seed data).
- Longer-lived sessions or a better session-continuation mechanism to avoid the context-compression overhead.

---

## 9. What's Next

The first sprint established the platform's structural foundations — stable IDs, the `/v2/` API shelf, provenance, plugin SPI, and a modernised frontend. The next frontier is researcher-facing quality of life: making the platform feel responsive, personal, and alive.

**Shipped in the continuation session (2026-05-19, afternoon):**

- **Personal landing page (#43) — shipped.** `PersonalDigest.vue` shows the 6 most-recently-updated accessible collections, a greeting card, and quick-action buttons. `useFetchRecentCollections` composable; `pages/index.vue` routes to digest when authenticated. 5 unit tests.

- **Auto-refresh on stale session (#49) — shipped.** `useAuthRefreshMiddleware` (wired into both API clients), `SessionExpiryWarning.vue`, and `chunk-error-recovery.client.ts` plugin. 8 unit tests. Covers the "annoying reload" (#45) for the non-navigation chunk-error path.

- **Per-kind reference counts on DataObject list (#37) — shipped.** `DataObjectListItemV2IO` with `timeseriesCount`, `fileCount`, `structuredDataCount`. Batch Cypher query; no N+1. `DataObjectV2RestTest` fixed and extended (17 tests).

- **API docs enrichment (#29/#31/#32) — shipped.** 7 v2 REST endpoints enriched with field-level response shapes, auth requirements, and Next-step links. Factual corrections to AnomalyDetectionRest defaults and TimeseriesReferenceV2Rest valid-value list.

- **Fork-vs-upstream comparison page — shipped.** `docs/comparison.md` with compact tables, ✓/📐/🗓 status indicators, registered in in-app /help nav.

**Near-term (designed, not yet shipped):**

- **Basic mode containerless UX (#51).** In basic mode, containers should be invisible — researchers attach files and timeseries directly to a DataObject. Routing and permission layers are in place; frontend routing and component change needed.

- **API-level integration test suite** (`aidocs/ops/75-api-integration-test-suite.md`). pytest + httpx suite against the live container using seed data; runs post-deploy in CI. Design in progress.

- **uPlot migration for live-mode chart (#56)** (`aidocs/ux/76-uplot-timeseries-chart.md`). Swap ECharts for uPlot (40× lighter, purpose-built for streaming). Design in progress.

**Longer-horizon design work:**

- **MCP plugin (#30).** The OpenAPI spec already carries `x-mcp-tool-name` and `x-mcp-side-effects` extensions (V2S1a). The next step is generating an MCP server from the `/v2/` spec and wiring it into a Matrix bot (MTX1b) or a standalone tool. This is a design-phase item: the generated client needs review for tool-call ergonomics before the extensions are finalised.

- **File preview and thumbnail SPI (#34).** Researchers want to see a thumbnail before opening a file reference. The right shape is a plugin SPI (`ThumbnailProvider`) with implementations for common types (image, PDF first page, video still via ffprobe). Relates to the file-storage SPI (FS1a) already landed; the SPI seam design is the open work.

- **uPlot migration for live-mode chart (#56).** The current timeseries chart (ECharts via `vue-echarts`) works but is heavy and struggles in live-mode update loops. uPlot is 40× lighter and purpose-built for streaming data. The migration is a chart-layer swap with no API changes — the blocker is agreeing on the interaction model (zoom, tooltip, crosshair) before starting the component rewrite.

The pattern for the next phase is the same as the first: design doc first, then implementation in a single focused session, then deploy and validate on the live hostname before moving on.

