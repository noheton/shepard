# `shepard-plugin-v1-compat` — **Phase 1 marker plugin** design

**Status.** Design, not implementation. Phase 1 sibling to
`aidocs/platform/103-v1-compat-plugin-extraction.md` (Phase 2 — the
full code-move).
**Snapshot date.** 2026-05-22.
**Companion docs.** `aidocs/platform/103` (Phase 2, the extraction),
`aidocs/platform/68` (prior compat design; see §0),
`CLAUDE.md §"API-version policy"` + §"Always: surface operator knobs",
`aidocs/34`, `aidocs/44`.
**Tracker hooks (no edits yet).** `aidocs/34` row "V1COMPAT.0 —
marker plugin shipped"; `aidocs/44` new row "shepard-plugin-v1-compat
(Phase 1: control plane)".

---

## 0. Relationship to siblings — read first

This doc is **Phase 1** of the v1 compat work. It moves **zero code**.
It ships the control plane, the deprecation observability, the admin
knob, and ownership-of-the-fixture-corpus. Every v1 REST class, every
IO, every service stays exactly where it is today.

Phase 2 (`aidocs/platform/103`) is the full code-move: the 29 v1 REST
classes + ~37 IOs move into the plugin JAR, and the 13 hard
clarifications it lists must be answered before that work starts.

Relationship to `aidocs/platform/68` (the prior "in-tree compat
adapter" design): **`[NEEDS-CLARIFICATION] 0.1` — supersede 68?**
> Context: #68 proposes an in-tree package rename to
> `de.dlr.shepard.compat.v1.*` + thin-adapter posture (v1 calls v2
> internally). Phase 1+Phase 2 reach the same end-state by a
> different route (plugin JAR boundary). #68's deprecation-logging
> aspects overlap with Phase 1; its package-rename aspect overlaps
> with Phase 2.
> Options:
>   A) **Supersede 68.** Mark `aidocs/platform/68` "superseded by 103
>      (Phase 2) + 103a (Phase 1)". 68's package-rename idea
>      becomes one possible Phase 2 implementation tactic, not its
>      own design.
>   B) **Sequence after 68.** 68's Phase A (package rename) lands
>      first as in-tree work; 103a + 103 follow. Matches `aidocs/103
>      §0` lean.
>   C) **Coexist.** 68 = optional "leaner image" path; 103a+103 = the
>      plugin path. Two compat designs, double maintenance.
> Lean: **A — supersede.** 103a + 103 form a clean Phase 1 → Phase 2
> lineage. 68 was signed off but never started; its package-rename
> is now Phase 2 implementation detail at most. Saves one design-doc
> from the live set; `aidocs/103 §0.1` flips lean from "A — sequence"
> to "Phase 2 incorporates 68's rename as a sub-task if useful".

**The Phase 1 promise.** When the plugin is built with default
config (`enabled=true`), the byte-for-byte v1 wire is **identical**
to today. No response shape change. No header change. No status code
change. The only observable difference: structured audit events for
v1 calls and the new `/v2/admin/legacy/v1/*` admin surface.

---

## 1. Why Phase 1 first — the operator value case

`aidocs/103 §F` estimates Phase 2 at **~30 agent-sessions across
8–12 calendar weeks** + 4–8 weeks for prerequisite work (the L2
shepardId Phase 2 chain, the shared-IO inventory spike, the SHACL
substrate). That's a **5–7 month** wait before any operator-visible
control surface ships.

Phase 1 ships **in 1–2 calendar weeks** and delivers four things
operators need *today*:

| Capability | Operator value | Available today? |
|---|---|---|
| Per-instance v1 enable/disable flag | Operator can run a "v1-off" canary instance to discover unannounced upstream-client dependencies | No |
| Audit log of every `/shepard/api/*` call with caller identity | Real data on which clients still hit v1, when, on which endpoints — drives the deprecation calendar | No |
| `/v2/admin/legacy/v1/stats` — calls-per-hour/day/week, distinct callers, top endpoints | Operators answer "is v1 actually still in use here?" without log-grepping | No |
| Frontend deprecation banner when a v1 call passes through the session | UX-level visibility for users still on legacy tooling; opt-in dismissal | No |

**The structural argument.** Phase 2's code-move is dominated by the
shared-IO inventory (`aidocs/103 §4.1`) — that's the
70%-of-effort decision. Phase 1 does none of that work; it just gates
v1 at the request boundary and instruments calls. Decoupling these
phases lets us ship operator value on a 1-week cycle while Phase 2's
inventory-spike + IO-disposition decisions cook in parallel.

Critically: **Phase 1's audit log becomes Phase 2's inventory data.**
By the time Phase 2 lands, every operator instance can answer "which
of the 29 v1 endpoints still has live callers, by client identity",
which directly de-risks Phase 2's deprecation timeline (`aidocs/103
§4.3`).

---

## 2. Phase 1 scope (IN)

Eight components. Narrow. Reviewable in one PR series.

| # | Component | Shape | Where |
|---|---|---|---|
| 1 | `:LegacyV1Config` Neo4j singleton | `{enabled: bool}` only (clarification 2); seeded from `application.properties` on first start; A3b pattern (mirror `:FeatureToggleRegistry`, `:SemanticConfig`, `:UnhideConfig`) | `plugins/v1-compat/src/main/java/.../entities/LegacyV1Config.java` |
| 2 | Cypher migration | One node, idempotent, fail-fast; reads deploy-time `shepard.legacy.v1.enabled` to seed | `backend/src/main/resources/neo4j/migrations/V63__Bootstrap_legacy_v1_config.cypher` (stays in core — migrations runner doesn't load plugin classes) |
| 3 | `/v2/admin/legacy/v1/config` REST | GET + PATCH (RFC 7396 merge-patch); `@RolesAllowed("instance-admin")`; mutations captured by `ProvenanceCaptureFilter` (PROV1a, automatic) | `plugins/v1-compat/src/main/java/.../resources/LegacyV1ConfigAdminRest.java` |
| 4 | `/v2/admin/legacy/v1/stats` REST | GET; counters (last hour / day / week / all), distinct callers (`sub` claim if JWT, else IP), top 10 endpoints by hit-count, last-call timestamp; same `@RolesAllowed` | `plugins/v1-compat/src/main/java/.../resources/LegacyV1StatsAdminRest.java` |
| 5 | `DeprecationLogFilter` | `ContainerRequestFilter` + `ContainerResponseFilter` pair, `@Provider`, `@PreMatching`. Matches `/shepard/api/...`. Per-request: emit one structured audit event (clarification 3) + add `Deprecation: true` + `Link: </v2/...>; rel="successor-version"` response headers (RFC 8594) + increment in-memory counters (TTL'd Caffeine cache; flushed to stats endpoint reads). Rate-limit log line to once per `(path, sub)` per process lifetime to avoid the home-showcase MQTT flood (`aidocs/103 R4`) | `plugins/v1-compat/src/main/java/.../filters/DeprecationLogFilter.java` |
| 6 | `@LookupIfProperty`-style runtime gate | When `:LegacyV1Config.enabled=false`, all `/shepard/api/...` requests return **410 Gone** (clarification 1) + RFC 7807 problem-detail body pointing at `/v2/`. Implemented as a separate `@Provider` filter (NOT via Quarkus `@LookupIfProperty` because that's build-time; we need runtime flippability per CLAUDE.md A3b precedence). Filter consults the singleton via cached read (5s TTL) — never queries Neo4j per-request. | Same file as DeprecationLogFilter, or sibling `LegacyV1GateFilter.java` |
| 7 | v5 fixture corpus migration | Move `backend/src/test/resources/fixtures/v5/` → `plugins/v1-compat/src/test/resources/fixtures/v5/`. Move `V5WireFidelityTest` + `V5JsonNormalizer` similarly. `docs/reference/v5-cross-instance-quirks.md` gets a path-update + a sentence pointing at the plugin module. (Clarification 4 — timing of this move) | `plugins/v1-compat/src/test/...` |
| 8 | Frontend deprecation banner | Session-level Vuetify banner that activates when any response in the session carried `Deprecation: true`. Dismissible per-session; tracks count of v1-tainted responses. Visible to all logged-in users (NOT admin-only — the user's tool is the v1 caller, they need to see it) | `frontend/components/context/legacy/V1DeprecationBanner.vue` + `frontend/composables/context/useV1Deprecation.ts` (intercept all `useApi`/`$fetch` responses for the header) |
| 9 | Plugin manifest + pom + scaffold | Mirror smallest plugin (`plugins/file-s3` or `plugins/wiki-writer`); `provided` scope on `backend`; Quarkus build-time CDI scan; included in `with-plugins` Maven profile (active by default) | `plugins/v1-compat/pom.xml`, `plugins/v1-compat/src/main/java/.../V1CompatPluginManifest.java` |
| 10 | Docs | `plugins/v1-compat/docs/{install,quickstart,reference}.md` per CLAUDE.md §"plugins ship their own documentation"; new `docs/reference/v1-deprecation.md` for end-user-facing context | `plugins/v1-compat/docs/`, `docs/reference/` |
| 11 | `aidocs/34` framing note | Single PR adds one paragraph at the top of `aidocs/34-upstream-upgrade-path.md`: "Rows in the table claim byte-stability of `/shepard/api/...`; from V1COMPAT.0 onward, that stability is conditional on `shepard-plugin-v1-compat` being installed (default-on) AND `:LegacyV1Config.enabled=true` (default true)." | `aidocs/34-upstream-upgrade-path.md` |

**What ships as a result.** A default Shepard build is byte-identical
on `/shepard/api/...` to today (response bodies, status codes,
headers — modulo the two added deprecation response headers, which
are RFC-standard and additive). Operators gain a knob, a stats
endpoint, and a frontend banner. Phase 2's eventual extraction work
inherits the audit log as inventory data.

---

## 3. Phase 1 scope (OUT — deferred to Phase 2)

Anything in this list is a Phase 2 concern; a Phase 1 PR that
attempts it should be rejected and rescoped.

- Movement of any v1 REST resource Java code.
- Movement of any IO class.
- The shared-IO disposition problem (`aidocs/103 §4.1`) — the
  inventory spike, the per-class tag (pure-v1 / share-stable /
  share-volatile), the mixin / duplicate / move choice.
- All 13 clarifications in `aidocs/103` except the ones reframed
  here (clarification 4.2 "default state" becomes trivially "yes,
  default-on, that's the whole Phase 1 contract").
- `aidocs/platform/68`'s in-tree package-rename — if `[NC] 0.1=A`
  this design supersedes that route.
- Per-endpoint disable (`aidocs/103 §5.5` — YAGNI per that doc;
  YAGNI doubly here).
- Deprecation timeline anchoring to versions (`aidocs/103 §4.3`).
  Phase 1 ships the observability; the calendar comes later
  informed by stats data.
- OpenAPI doc-gen routing (`aidocs/103 §5.7`) — no code moves, so
  the existing classpath-scan still works unchanged.
- Removing the v5 fixture corpus from `backend/src/test/...`
  permanently — see clarification 4 for the timing question.

---

## 4. Hard clarifications — 4 forks

### `[NEEDS-CLARIFICATION] 1` — disabled-state HTTP status

> Context: when an operator sets `:LegacyV1Config.enabled=false`, every
> `/shepard/api/...` request should return a clear failure status.
> RFC 7231 §6.5.9 410 Gone = "intentionally removed; permanent";
> 404 = "not found"; 503 = "temporarily unavailable".
> Options:
>   A) **410 Gone** + RFC 7807 problem-detail body + `Link: </v2/…>; rel="successor-version"`. Semantically precise; clients with proper status-code handling escalate immediately.
>   B) **404 Not Found.** Familiar; doesn't surprise older clients. But misleading — the resource isn't gone, the surface is administratively disabled.
>   C) **503 Service Unavailable** + `Retry-After: <bignum>`. Implies temporary; legacy clients may auto-retry forever; wrong semantic when the operator's intent is "off permanently for this instance".
> Lean: **A — 410 Gone.** Matches operator intent ("v1 is administratively removed on this instance"); RFC 7807 body gives a migration pointer; the `Link` header lets sophisticated clients auto-follow. Risk: some upstream client libraries crash on unexpected 410 — but those clients were going to break at Phase 2 anyway; better an early loud signal than a misleading 404.

### `[NEEDS-CLARIFICATION] 2` — `:LegacyV1Config` shape

> Context: the singleton needs at minimum an `enabled` flag. Question:
> what else, if anything, in Phase 1?
> Options:
>   A) **Minimal — `{enabled: bool}`.** One toggle. Everything else is Phase 2 or YAGNI.
>   B) **Enabled + deprecation log level enum.** `{enabled: bool, deprecationLogLevel: "audit-only" | "audit+warn" | "audit+warn+stdout"}`. Operators with downstream log pipelines pick noise level.
>   C) **Enabled + per-endpoint disable map.** `{enabled: bool, disabledRootPaths: ["/shepard/api/users", …]}`. Granular control over which v1 root-paths return 410 while others still serve.
> Lean: **A — minimal.** Phase 1's purpose is the simplest possible operator knob. The log level is set by core logging config (`application.properties`), not by the singleton. Per-endpoint disable has no current use case (`aidocs/103 §5.5` already calls it YAGNI). Adding fields later via additive Cypher migration is cheap; removing them is expensive.

### `[NEEDS-CLARIFICATION] 3` — deprecation log mechanism

> Context: every v1 call needs to leave a durable trace for operator
> analytics. Three mechanisms exist in this codebase today.
> Options:
>   A) **`logger.info(...)`** with structured MDC fields (path, sub, ip, ts). Cheap; greppable; no DB write; lost when log rotation purges. Operators with a log aggregator (Loki, ELK) get it for free.
>   B) **`logger.warn(...)`** same fields. Same as A but trips alert thresholds in monitoring stacks. Risk: at MQTT-collector telemetry rate (home-showcase calls `/shepard/api/timeseriesContainers/{id}/payload` continuously) this floods both logs and pagers.
>   C) **`:Activity` audit event** via the existing `ProvenanceCaptureFilter` (PROV1a) infrastructure — stamp `prov:wasInvokedVia = shepard:v1Surface` on every captured activity. Queryable forever via Cypher; survives log rotation; integrates with existing audit query tooling; the natural data source for `/v2/admin/legacy/v1/stats`. Cost: every v1 request adds one Neo4j write — that's exactly what PROV1a already does for admin mutations, but admin mutations are rare; v1 reads (especially timeseries payload polls) are not.
>   D) **Hybrid — in-memory counter for stats endpoint + per-`(path, sub)` once-per-process WARN-on-stdout + AUDIT event only for write operations (POST/PUT/PATCH/DELETE).** Reads are common and cheap to count in-memory; writes are rare and worth the durable audit row. Matches `aidocs/103 §5.4` lean for warn-once-per-pair.
> Lean: **D — hybrid.** Reads via in-memory Caffeine counters (flushed to stats endpoint reads, never written per-request); writes via `:Activity` audit row (already cheap, already paid for in PROV1a). Once-per-(path, sub)-per-process WARN on stdout for operator visibility without flood. Critically, this means the home-showcase MQTT collector's high-rate v1 reads cost nothing in DB writes. If clarification 3 = C, the home-showcase rate-test must precede landing.

### `[NEEDS-CLARIFICATION] 4` — fixture corpus move timing

> Context: `backend/src/test/resources/fixtures/v5/` is the v1 wire
> contract. Phase 1 owns the deprecation observability; Phase 2 owns
> the code move. When does the fixture corpus move?
> Options:
>   A) **Phase 1 — move with the marker plugin.** Co-locate the
>      contract with its owner-module from day one. Risk: Phase 2's
>      code-move will need to keep paths consistent; Phase 1 is doing
>      a directory shuffle that benefits Phase 2 but not Phase 1
>      directly.
>   B) **Phase 2 — move when the code moves.** Tight coupling between
>      tested-code and test-fixtures. Phase 1 just ships
>      observability; the contract location is invariant until
>      Phase 2.
>   C) **Stay in core forever — even after Phase 2.** Matches
>      `aidocs/103 §5.1.C` lean ("core, no-op gracefully when plugin
>      absent"). Phase 1 changes nothing about the fixtures.
> Lean: **C — stay in core, link from plugin module.** This sidesteps
> the move entirely; `V5WireFidelityTest` in core continues to assert
> against the corpus in core; the corpus IS the contract. Phase 2
> handles the test-phase-routing question (Surefire / Failsafe /
> integration module). Phase 1 just adds a one-line README note in
> the fixtures directory pointing at the plugin module: "this corpus
> defines the wire contract guaranteed by shepard-plugin-v1-compat."
> This flips `aidocs/103 §5.1` from "C — no-op gracefully" to "C and
> defer move indefinitely; the corpus is core-owned because it's
> cross-cutting." Phase 1 still includes this README update as a
> deliverable.

---

## 5. PR sequence

Three PRs, each independently mergeable, each reviewable in <30
minutes.

| PR | Title | Deliverables | Risk |
|---|---|---|---|
| PR-1 | `plugins/v1-compat` module scaffold + singleton | New module dir; pom.xml mirroring `plugins/file-s3`; `V1CompatPluginManifest.java`; `:LegacyV1Config` entity + DAO; idempotent fail-fast Cypher migration `V63__Bootstrap_legacy_v1_config.cypher`; `with-plugins` profile updated; `:LegacyV1Config` Cypher migration test (testcontainer fixture) | Low — additive module, no behaviour change |
| PR-2 | Admin REST + deprecation filter + gate | `/v2/admin/legacy/v1/config` GET+PATCH; `/v2/admin/legacy/v1/stats` GET; `DeprecationLogFilter` (structured audit per clarification 3); `LegacyV1GateFilter` (returns 410 per clarification 1 when `enabled=false`); fixture-corpus README update (clarification 4 — pointer only, no move); CLI parity `shepard-admin legacy v1 {status,enable,disable}`; integration tests covering: enabled+v1-call=200, disabled+v1-call=410, header `Deprecation: true` present on enabled response, stats endpoint counts increment | Medium — adds two filters on the v1 request path; needs careful ordering vs `JWTFilter` + `PermissionsFilter` |
| PR-3 | Frontend banner + docs + aidocs/34 framing | `V1DeprecationBanner.vue` + `useV1Deprecation.ts` composable; response interceptor in `frontend/composables/useApi.ts`; Vitest coverage; `plugins/v1-compat/docs/{install,quickstart,reference}.md`; `docs/reference/v1-deprecation.md`; one-paragraph framing note at top of `aidocs/34-upstream-upgrade-path.md`; `aidocs/44` row "V1COMPAT.0 — marker plugin shipped" | Low — pure frontend + docs |

**Effort estimate.** 3 PRs × ~1 agent-session each = **3
agent-sessions across 1–2 calendar weeks.** Compared with `aidocs/103
§F`'s ~30 sessions / 8–12 weeks for Phase 2.

---

## 6. Risk register

| # | Risk | Probability | Blast radius | Mitigation |
|---|---|---|---|---|
| R1 | Operator flips `enabled=false` on an instance where downstream tooling silently depends on `/shepard/api/...` | MEDIUM | Production outage for one operator's downstream | (a) stats endpoint must show >0 calls in last 7d before any UI suggests flipping the toggle; (b) admin UI flip dialog shows last-week's caller IPs/subs; (c) PATCH endpoint with `enabled=false` requires explicit `?confirm=true` query param when stats show recent activity |
| R2 | `DeprecationLogFilter` adds latency or fails on the v1 request path, breaking byte-fidelity | LOW | Every v1 caller sees 5xx | Filter logic stays in-memory only (Caffeine cache, never blocks on Neo4j); write path (`:Activity` audit for writes) is fire-and-forget on a bounded executor; integration test confirms response body byte-identical with filter enabled vs disabled |
| R3 | Two added response headers (`Deprecation`, `Link`) cause a brittle upstream client to misbehave | LOW | Single-client breakage | Headers are RFC-standard and additive; document in `aidocs/34` framing note; if a client breaks, operator workaround = `:LegacyV1Config.suppressDeprecationHeaders=true` — but that's clarification-2 territory (Phase 1 lean = minimal config; defer to follow-up if reported) |
| R4 | The plugin's CDI scan misses the filters on some Quarkus version | LOW | All v1 calls silently un-instrumented (no audit, no headers) but still 200 OK | Mirror `plugins/file-s3` pattern; CI smoke test confirms `Deprecation: true` header on default build |
| R5 | Phase 1 ships, then Phase 2 design takes 6+ months — operators get used to "marker plugin" and resist the eventual code move | MEDIUM | Schedule friction for Phase 2 | Phase 2 is byte-invariant by design (same `aidocs/103 §4.2.A` lean); Phase 1 docs explicitly frame this as Phase 1, not the end-state; `aidocs/44` row tracks Phase 2 as "designed, not started" |

---

## 7. Persona review slate

Phase 1 is narrow operational infra. The big architectural questions
(shared-IO disposition, deprecation calendar, monolith-vs-split,
service-DTO-neutrality) are deferred to Phase 2; persona-relevance is
correspondingly narrower.

| # | Persona | Why this phase |
|---|---|---|
| 1 | **API Scrutinizer (Minimalist)** | Phase 1 adds two response headers + a 410 status + two admin endpoints. Adjudicate: are the headers RFC-compliant? Is 410 the right disabled-state code (clarification 1)? Is the stats endpoint shape minimal? |
| 2 | **Reluctant Senior Researcher** | The operator at the receiving end. The whole knob exists for this persona. Sanity-check: would they understand the admin UI? Would they trust the stats numbers? Is the frontend banner reassuring or alarming? |

Lower priority (skip for Phase 1; revisit at Phase 2):

- Strategy Aligner — deprecation calendar is Phase 2's concern.
- Industrial Ecosystem Advocate — upstream-friendliness unchanged by Phase 1 (default-on, byte-identical).
- Digital Native Researcher — no API generation impact; OpenAPI unchanged.
- Core Tech & UX Auditor — frontend banner is the only UI surface; review as part of PR-3.
- Data & Process Ontologist / Quality Engineer / RDM / Analytics-AI — no relevance for control-plane work.

---

## 8. What this design does NOT propose

- Does **not** move any code from `de.dlr.shepard.*` to the plugin.
- Does **not** change a single byte of any v1 response when
  `:LegacyV1Config.enabled=true` (the default and shipping state),
  except for two RFC-standard additive response headers
  (`Deprecation`, `Link`).
- Does **not** deprecate `/v2/`.
- Does **not** ship a deprecation timeline / removal date — that's a
  separate strategy decision, informed by Phase 1's stats data.
- Does **not** rename any Java package (the rename idea from
  `aidocs/platform/68` is now Phase 2 territory, if needed at all).
- Does **not** split the v5 fixture corpus location — it stays in
  core (clarification 4).
- Does **not** introduce a new SPI; reuses existing Quarkus
  build-time CDI scan + the established `:*Config` A3b pattern.

---

## 9. Tracker hooks (do not edit in this design PR)

The PR series must touch these files when it implements Phase 1.
Listed here so the implementing agent can find them; DO NOT edit them
in the design-doc PR.

- `aidocs/34-upstream-upgrade-path.md` — one-paragraph framing note
  at the top; new row "V1COMPAT.0 — marker plugin (control plane,
  deprecation observability, admin knob); v1 byte-stability now
  conditional on `:LegacyV1Config.enabled=true` (default true)".
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — new row
  "shepard-plugin-v1-compat (Phase 1 marker)".
- `aidocs/platform/103-v1-compat-plugin-extraction.md` — if
  clarification 0.1 resolves to `A — supersede 68`, then `§0.1`
  there flips lean to "Phase 2 incorporates 68's rename as a
  sub-task if useful"; `§5.1` lean updated per clarification 4
  here.
- `aidocs/platform/68-v2-baseline-v1-compat-layer.md` — if
  clarification 0.1 = A, append "**Superseded by `aidocs/platform/103`
  + `aidocs/platform/103a`**" header at top.
- `CLAUDE.md` §"API-version policy" — append one sentence: "v1
  byte-stability is enforced by `shepard-plugin-v1-compat` and gated
  by `:LegacyV1Config.enabled`."
- `docs/reference/v1-deprecation.md` — new end-user-facing page
  explaining the banner and what to do about it.
- `docs/reference/v5-cross-instance-quirks.md` — one cross-reference
  line: "the wire contract here is enforced by
  shepard-plugin-v1-compat; the matching corpus is at
  `backend/src/test/resources/fixtures/v5/`."
- `plugins/v1-compat/docs/{install,quickstart,reference}.md` — three
  new files per CLAUDE.md §"plugins ship their own documentation".

---

## 10. Summary — clarification index

| ID | One-line question | Lean |
|---|---|---|
| 0.1 | Supersede / sequence / coexist with `aidocs/platform/68`? | **A — supersede** |
| 1 | Disabled-state HTTP status? | **A — 410 Gone + RFC 7807** |
| 2 | `:LegacyV1Config` shape? | **A — minimal `{enabled: bool}`** |
| 3 | Deprecation log mechanism? | **D — hybrid (in-memory counters + once-per-(path,sub) WARN + audit for writes only)** |
| 4 | Fixture corpus move timing? | **C — stay in core; README pointer only** |

**5 clarifications** (within the 3–5 target). Implementation can
start after clarification 1 and 3 are answered; 0.1, 2, and 4 can be
adjudicated during PR review.
