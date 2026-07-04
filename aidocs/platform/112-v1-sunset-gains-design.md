---
stage: feature-defined
last-stage-change: 2026-05-31
audience: maintainer + operator
owner: strategy
---

# 112 — What we'd gain by giving up v1 compatibility

**Status.** Strategy / decision-support doc, not implementation. The
output is a recommendation the operator can act on.
**Snapshot date.** 2026-05-31.
**Companion docs.** `CLAUDE.md §"API-version policy"` +
`§"Always: maintain the upstream upgrade path"`;
`aidocs/34-upstream-upgrade-path.md` (admin-facing ledger);
`aidocs/platform/25-neo4j-id-migration-design.md` (the L2 chain that
the `/v2/` shelf formalises);
`aidocs/platform/68-plugin-vs-core-overview.md` (the prior in-tree
compat-adapter design);
`aidocs/platform/103-v1-compat-plugin-extraction.md` (Phase 2 — full
code-move);
`aidocs/platform/103a-v1-compat-marker-plugin.md` (Phase 1 — marker
plugin, **already shipped**);
`aidocs/agent-findings/ui-scrutinizer-2026-05-31.md`
(BUG-COLL-APPID-ROUTE-002 — the live trigger);
memory `feedback_appid_to_shepardid.md` (the deferred rename);
memory `project_v1_sunset_strategy.md` (current per-operator policy).

**Tracker hooks.** `aidocs/34` row pending operator decision;
`aidocs/44` row pending operator decision; `aidocs/16` rows
`V1-SUNSET-01..08` drafted in §8.

---

## §1 The question

The operator asked, on 2026-05-31, in plain language:

> "What would we gain by giving up v1 compatibility?"

The question matters **now** because the live deployment is
demonstrably suffering a half-migration. `BUG-COLL-APPID-ROUTE-002`
(documented in `aidocs/agent-findings/ui-scrutinizer-2026-05-31.md`)
is a CRITICAL severity finding: `/collections/{uuid-v7}` routes
correctly reach the page handler (M's wave-5 parser fix held) **but
the downstream composables still call the generated v1 client which
expects numeric `id`**, so the data load 404s with a red toast on
the user-facing surface. The Collections feature is half-broken on
the live deployment **right now**.

The bug is not *caused* by v1 existing — it's caused by composables
still using the v1 generated client. But v1's *existence* enabled
the half-migration state, and the *contributor cognitive load of
shipping fixes against two surfaces in parallel* is what let
BUG-COLL-APPID-ROUTE land partially fixed and ship. Causation
matters because the doc's job is not "is v1 to blame for this bug"
but "what changes if v1 stops being a constraint we reason about?"

There is also a standing policy at issue. `project_v1_sunset_strategy.md`
records the 2026-05-22 decision: **per-operator runtime toggle, no
fork-imposed sunset, plugin stays in the codebase indefinitely as a
maintained module**. The operator's question implicitly asks whether
to revisit that decision.

To keep the doc honest the §1 frame fixes two things up front:

1. **Scope of "give up v1."** This doc evaluates two distinct
   gestures and is clear about which §3 entries flow from which:
   - **(W) Wire-freeze.** v1 stays callable to operators who flip
     `:LegacyV1Config.enabled = true` (current default), but the
     contributor mainstream becomes v2-only. No new v1 endpoints.
     New IO shapes never enter `BasicEntityIO`. v1 is a frozen
     compatibility surface served by the marker plugin (already
     shipped) and — when extracted (Phase 2, `aidocs/platform/103`)
     — by the `shepard-plugin-v1-compat` JAR. Operators are unaffected.
   - **(D) Drop-from-codebase.** The 29 `*Rest.java` v1 files, the
     38 non-`v2.*` IO shapes, the 1196 LoC of `*V5WireFidelityIT`,
     the `:LegacyV1Config` singleton, and the `@dlr-shepard/backend-client`
     generated TS client all go away. Operators who were on
     `/shepard/api/...` lose that surface entirely on the upgrade
     that ships the deletion.
2. **Causal language about bugs.** Below, when §3 claims a bug
   becomes "structurally impossible" by dropping v1, it means **(D)**.
   When it claims a bug becomes "fixed by attrition" or "lower-cost
   to fix", it means **(W)**. The two postures have different
   evidence and different operator costs.

§7 picks one.

---

## §2 What v1 compat costs us today

Concrete numbers from a 2026-05-31 walk of the tree. All counts are
reproducible via `find … | wc -l` / `find … | xargs wc -l`.

### §2a Code surface

| Surface | Files | LoC | Deletion safety |
|---|---|---|---|
| **v1 `*Rest.java` (non-`v2.*`)** | 29 | 6,174 | Fully deletable under (D). |
| **v1-shared IO classes (non-`v2.*`)** | 38 | 2,242 | Partial — `BasicEntityIO` is the shared superclass for v2 IOs too. Deletable under (D) only after a Java-internal rename pass that lifts the shared fields into `AbstractV2IO` or similar. |
| **`v1-compat` plugin (Phase 1 — marker plugin, shipped)** | 21 | 2,923 | Fully deletable under (D); irrelevant under (W) since the plugin **is** the wire-freeze. |
| **Backend test surface — `*V5WireFidelityIT` + `V5WireFidelityTest` + `V5JsonNormalizer*`** | 16 | 1,196 | Fully deletable under (D). |
| **Frontend generated client (`backend-client/`)** | 282 (src) / 420 (incl. dist) | 36,779 (incl. dist) | Fully deletable under (D) if every composable migrates to `useV2ShepardApi`. Today **53 composables still call `useShepardApi` (v1)** vs. 26 calling `useV2ShepardApi`. |
| **Frontend v1-usage composables** | 53 (of ~79 total) | ~2,500 (estimated) | Migrate, not delete. |

The **honest deletable surface** under a (D) drop is roughly:

- Hand-written backend Java: ~6,174 (REST) + ~1,196 (tests) +
  ~2,923 (compat plugin) + a fraction of the 2,242 IO LoC that
  isn't shared with v2 = **~10,500–11,500 LoC** in the backend
  module.
- Generated TS client: **~36,779 LoC** wholesale deletable, but
  this is generated; saving it costs zero contributor attention
  beyond "did the OpenAPI generator job succeed today." The real
  cost is the 53 composables that consume it.

Do not overclaim. The 63,295 LoC "everything in `de.dlr.shepard/`
not under `v2/`" figure includes the auth/permission/migrations/SPI
core that v2 also uses. The deletable hand-written backend code is
under 12K LoC.

### §2b Test surface

Wirefidelity is the bulk of the cost we can quantify:

| Test class | Purpose | LoC |
|---|---|---|
| `V5WireFidelityTest` | Abstract base — pins every v1 endpoint's JSON byte-shape against the v5.2.0 upstream OpenAPI spec. | ~200 |
| `V5JsonNormalizer*` (2 files) | Whitespace + key-order normalisation so byte-comparison is deterministic. | ~250 |
| `WireFidelityMismatchException` | Typed failure carrying the offending diff. | ~30 |
| `*V5WireFidelityIT` (12 IT classes — Collection, DataObject, ApiKey, User, UserGroup, Permissions, SemanticAnnotation, Subscription, Container, TimeseriesReference, StructuredDataReference, FileReference) | Each pins one resource's create/read/update/delete shape against the pinned fixture under `backend/src/test/resources/fixtures/v5/`. | ~720 |
| `V1WireFidelityTest` (in `data/timeseries/io/`) | The original wire-fidelity test (per `feedback_appid_to_shepardid.md` PR `b943b1c5`) asserting `BasicEntityIO`'s key set is exactly v5.2.0 and contains no `shepardId` field. | ~50 |
| **Total** | | **~1,196 LoC** |

Plus the v5 fixture corpus under `backend/src/test/resources/fixtures/v5/`
(10 JSON fixture files per `aidocs/platform/103`). Each new endpoint
that lands as part of v1 (a 5.2.0-compatible additive) pays a new
fixture + a new IT. Each PR that wants to widen a v1 IO shape must
either suppress the test or land a fixture update + a "this is a
deliberate divergence from upstream" rationale in `aidocs/34`.

37 test files outside the wirefidelity package also reference
`/shepard/api/` or `Constants.SHEPARD_API` — they would need
attention (most are likely path-only, easy mechanical change) on a
(D) drop.

### §2c Cognitive surface

This is the cost the file-count doesn't capture.

Every contributor making a change touching IO shapes, REST paths,
or response envelopes has to reason about:

1. **The two-surface mapping.** v1's `/shepard/api/collections/{id:long}`
   ↔ v2's `/v2/collections/{appId:uuid-v7}`. The L2c phase landed
   `EntityIdResolver` (a `@RequestScoped` bean) translating
   `Long` ↔ `appId` at the DAO boundary; that bean exists because
   v1 ships long IDs and v2 ships appIds.
2. **The `BasicEntityIO` shared-superclass gotcha.** The
   2026-05-22 finding by the `shepardId` rename agent: every
   `*IO` class in v2 (`v2.DataObjectIO`, `v2.CollectionIO`, …)
   extends `BasicEntityIO`, and **the same `BasicEntityIO` is
   serialised by v1 resources too**. Renaming `appId → shepardId`
   on `BasicEntityIO` leaks `shepardId` into the v1 wire and breaks
   v5 byte-fidelity. The mandatory pattern (memory
   `feedback_appid_to_shepardid.md`):
   ```
   ADD an additive @JsonProperty("shepardId") getter on the v2
   SUBCLASS only (e.g. v2/DataObjectIO).
   NEVER touch BasicEntityIO directly.
   ```
   Every PR adding a field that should appear under a fork-friendly
   name has to pay this tax.
3. **The L2c partial state.** L2 split the codebase so that
   `WHERE ID(e) = $entityId` (`long`) became `WHERE e.appId = $appId`
   (`String`) in 14 DAO files, but `PermissionsService.isAllowed`
   path-segment dispatch and `Neo4jQueryBuilder`'s search-derived
   numeric-id predicates were "deliberately not touched (those land
   in L2d). L2d still gated on P4 + H4." So contributors touching
   permissions or search reason about long-id pathways that exist
   only because v1 ships them.
4. **The marker plugin's idiosyncrasies.** Filters
   (`LegacyV1GateFilter`, `LegacyV1DeprecationFilter`) wrap every
   v1 response with the X-Shepard-Legacy header (per V1COMPAT.0)
   and gate every v1 request against `:LegacyV1Config.enabled`. A
   contributor adding a new public endpoint has to ask: "is this
   v1 or v2?" — answering wrong has wire-fidelity consequences.

The cognitive surface is not directly measurable but is what the
operator's question is *really* probing. Two pieces of evidence
make it concrete:

- The `shepardId` rename had to be split into **two phases** —
  Phase 1 (#58, wire/IO rename) and Phase 2 (#123, deep Java-internal
  rename + Cypher property-key migration) — *specifically because*
  v1 wire fidelity forced an additive pattern in the IO layer. A
  one-phase rename would have been straight-line work. The dual-phase
  is the contributor tax v1 imposes.
- The L2c partial state is the same shape — a chunk of the codebase
  is straight-line v2, another chunk is dual-id translation glue,
  and a third chunk hasn't been touched yet because L2d is gated.

### §2d Active bug surface

| Bug ID | Severity | What broke | Causal relationship to v1 |
|---|---|---|---|
| `BUG-COLL-APPID-ROUTE-001` | (fixed wave 5) | Frontend route parser truncated UUID v7 to numeric prefix via `parseInt` because the `[collectionId]` route param was typed `number`. | Caused by the frontend assuming v1 numeric ids. Would not have been written under a v2-native client. |
| `BUG-COLL-APPID-ROUTE-002` | **CRITICAL — live now** | After M's parser fix landed, `/collections/{uuid}` reaches the page handler but the downstream composables call the generated v1 client which expects a numeric id; backend returns 404; user sees red toast + "Not found" view. | The composables exist in their current shape *because* the generated v1 client is the default `useShepardApi`. Under (D) the v1 client is deleted and this bug shape is structurally impossible — there's no client expecting a numeric id. Under (W) the bug remains the same fix difficulty; v1 doesn't change. |
| `ROLE-GRANT-STALE-SESSION-01` (shipped runbook fix) / `-02 -03 queued` | Major | `:User.username` = OIDC `sub` UUID, not display name; role grants via Cypher need a sign-out before the JWT picks them up. | v1's `/users/{username}` and v2's `/v2/admin/instance-admins` both grant roles; the dual-surface contributed to the ambiguity about which path is authoritative. Not v1-caused, but v1-aggravated. |
| `ApiKeyV5WireFidelityIT` (regression test that was added 2026-05-27) | (fix-of-debt) | A wire-suppression of `POST /shepard/api/users/{u}/apikeys` was suspected to be the cause of 405 responses 2026-05-23; investigation found it was a deployment-state artifact, not code. Test added to make future suppression breakable at build time. | Pure v1 maintenance cost — a wirefidelity IT class added solely to keep the v1 surface honest. Under (D) the test is deletable. |

The honest framing per the advisor: **v1's existence didn't write
BUG-COLL-APPID-ROUTE-002**. The composables shipping against the v1
client did. But the half-migration state — some composables on v2,
most still on v1 — exists *because* both surfaces are live, and the
contributor cognitive load of dual-surface ships these bugs in
clusters.

### §2e Migration tax

Every coordinated migration pays a v1 tax. Two concrete cases:

1. **`appId → shepardId` rename (PRs #58 + #123).**
   - Phase 1 (#58, queued): wire/IO surface rename only — `/v2/`
     REST `appId` → `shepardId`, IO class JSON field names, MCP
     tool schemas, frontend backend-client + Vue. Java internals
     untouched.
   - Phase 2 (#123, queued, gated): deep Java-internal rename +
     Cypher property-key migration.
   - Under (D), Phase 1 + Phase 2 collapse into one straight-line
     pass. The whole reason they're split is `BasicEntityIO` —
     the shared superclass between v1 and v2 IOs. Drop v1, drop
     the shared-superclass constraint, and you can rename
     `appId → shepardId` at the field level in one PR.
   - **This is the single most expensive ongoing cost of v1 compat.**
     Every PR in the rename series writes the additive
     `@JsonProperty("shepardId")` pattern; every contributor
     learns the pattern; every reviewer enforces it. Under (D)
     all of that vanishes.

2. **SHACL substrate split (`feedback_shacl_single_source_of_truth.md`).**
   - SHACL substrate is the planned single source of truth for
     domain data; the existing relational/property-graph projections
     become projections **over** SHACL.
   - The v1 wire is itself a projection over today's data model.
     Under (W) it stays a projection over a different substrate;
     under (D) it goes away.
   - Contributors building the SHACL substrate have to preserve
     the v1 wire byte-shape over the *substrate change*; this is
     a non-trivial coordination cost. (W) makes it easier than
     today (the rules are clearer); (D) makes it disappear.

3. **L2d (`/v2/` shelf with `appId` as native identifier).**
   - `aidocs/platform/25 §3.2`: "The `/v1/...` paths keep `long`
     for one deprecation window (≥ 2 minor releases…). Inside
     the backend the v1 path is a thin translator: read the long
     `pathParam`, look up the node by internal id, project
     `appId`, then run the same Cypher as the v2 path."
   - L2d is the formalisation of the split. Under (W) L2d ships
     as designed. Under (D) L2d simplifies to "drop the long
     pathways entirely."

4. **L2e (drop the upstream `/v1/` long-id paths after a
   deprecation window).** This is the document's literal "if we
   decide to drop v1" milestone. The question this design answers
   is "do we trigger L2e."

A rough heuristic from the doc evidence: **every migration pass on
load-bearing identity gets 2–3× the touch-point count because v1's
wire-fidelity surface has to be preserved through the transform.**

---

## §3 What we'd gain by dropping v1

Each subsection labels the gain (W) wire-freeze-only or (D) full
drop. Some gains are partial under (W) and full under (D); the
distinction is recorded.

### §3a Code simplification

- **(D)** Delete 29 `*Rest.java` files / 6,174 LoC under
  `de.dlr.shepard.{context,data,auth,common}/.../endpoints/`.
  Examples: `CollectionRest.java`, `DataObjectRest.java`,
  `FileRest.java`, `TimeseriesRest.java`, `SearchRest.java`,
  `SubscriptionRest.java`, `ApiKeyRest.java`, `UserRest.java`,
  `UserGroupRest.java`, plus 8 reference-class Rests
  (`*ReferenceRest.java`) and 5 semantic Rests.
- **(D)** Delete 12 `*V5WireFidelityIT.java` files + 4 supporting
  classes / 1,196 LoC.
- **(D)** Delete the v5 fixture corpus
  (`backend/src/test/resources/fixtures/v5/`).
- **(D)** Delete the entire `@dlr-shepard/backend-client/` workspace
  module / 36,779 LoC. (Tooling change: drop the OpenAPI generator
  job; v2 only needs `useV2ShepardApi`.)
- **(W)** No direct code deletion. The cost in §2c (cognitive
  load) reduces because contributor mainstream goes v2-only — new
  IO fields land directly on v2 subclasses without the additive
  `@JsonProperty` dance — but the LoC stays.

### §3b Frontend simplification

- **(D)** Delete 53 v1-consuming composables' v1 fallback codepaths;
  migrate them to `useV2ShepardApi` (1:1 mechanical change for
  about 35 of them; 18 need real refactor because the v1 endpoint
  shape doesn't 1:1 map to v2).
- **(D)** `BUG-COLL-APPID-ROUTE-002` becomes structurally
  impossible: there is no client that expects a numeric id.
- **(D)** The frontend route types simplify — every `[id]`
  segment is `string` (uuid-v7), no more "is this a numeric id or
  an appId" disambiguation in `frontend/utils/collectionRouteParams.ts`
  and friends.
- **(W)** BUG-COLL-APPID-ROUTE-002 is the same fix difficulty as
  today; it just stops being a *recurring* bug shape because
  contributor mainline stops shipping new v1-coupled composables.

### §3c Schema simplification

- **(D)** The numeric `id` column on every Neo4j entity exists
  because v1 ships it in path parameters. Drop v1 → drop the
  promise to expose Neo4j-internal `id()` values → the numeric
  field can be deprecated, then dropped in a follow-on V##
  migration (paired rollback file).
  - `EntityIdResolver` becomes unnecessary; DAOs query directly
    by `appId`.
  - `AbstractEntity.id` (the OGM-mirrored `Long`) is still needed
    by Neo4j-OGM until the OGM-removal pass on
    `aidocs/archive/03-issues-status.md` #274 lands; but the
    *exposure* of `id()` at the API layer goes away. The internal
    `Long` becomes a pure persistence concern.
- **(W)** No schema change. The numeric id stays exposed via
  `/shepard/api/...` indefinitely.

### §3d Cypher simplification

- **(D)** The 14 DAO files that L2c flipped from
  `WHERE ID(e) = $entityId` to `WHERE e.appId = $appId` no longer
  need their `findByLegacyNumericId` overloads (where they exist)
  for v1 path dispatch.
- **(D)** `PermissionsService.isAllowed` (currently has a long-id
  path-segment dispatch per L2c §3.3) is rewritten as a
  uuid-only dispatch. The cache key (`long`) gets coupled to
  `appId` natively.
- **(D)** `Neo4jQueryBuilder`'s search-JSON-derived numeric-id
  predicates (deferred from L2c to L2d) become moot.
- **(W)** Same as today; L2d ships as planned.

### §3e Migration freedom — **the single biggest gain**

This is the gain the advisor flagged and the doc emphasises:

- **(D)** `appId → shepardId` (PRs #58 + #123) collapses from
  two coordinated phases into one straight-line pass:
  - Rename `appId` to `shepardId` directly on `BasicEntityIO`
    (which becomes a v2-only superclass; no v1 IO inherits from
    it anymore).
  - Drop the additive `@JsonProperty("shepardId")` getter
    pattern from `feedback_appid_to_shepardid.md`.
  - Drop the v1 wire-fidelity check
    `V1WireFidelityTest.shouldNotContainShepardIdField()`.
  - The Cypher property-key rename
    (`SET n.shepardId = n.appId REMOVE n.appId`) becomes a
    single idempotent migration with rollback twin.
  - Estimated calendar saving: **2-3 months** of phased rollout
    becomes 1 PR series.
- **(D)** SHACL substrate-split (task #120) stops needing to
  preserve v1 wire byte-shape over the substrate transformation.
  The split becomes purely structural.
- **(D)** Future renames cost half. The two-phase
  "wire-rename-first, internals-rename-later" pattern that the
  rename agent established 2026-05-22 becomes unnecessary.
- **(W)** Phase 1 + Phase 2 of the rename are still required.
  The substrate split still preserves v1 byte-shape. No saving
  on calendar time. **(W) does not deliver this gain.**

### §3f Plugin simplification

- **(D)** Delete the v1-compat plugin (`plugins/v1-compat/`,
  21 files, 2,923 LoC). The marker-plugin filters
  (`LegacyV1GateFilter`, `LegacyV1DeprecationFilter`,
  `LegacyV1FilterRegistration`) go away.
- **(D)** The `:LegacyV1Config` singleton + the
  `V63__Bootstrap_legacy_v1_config.cypher` migration get a
  rollback file (`V##_R__Drop_legacy_v1_config.cypher`).
- **(D)** The future Phase 2 v1-compat extraction
  (`aidocs/platform/103`) — 13 open clarifications, 600+ design
  lines — becomes moot. The whole design doc moves to
  `aidocs/archive/`.
- **(W)** Plugin stays. Phase 2 extraction can still ship if the
  architectural-cleanliness motivation persists.

### §3g Operator simplicity

- **(D)** `aidocs/34`'s "API-version policy" chapter shrinks.
  No "v1 vs v2" disambiguation in operator docs.
- **(D)** `docs/admin/runbooks/14-role-grants.md` and similar
  runbooks drop the "v1 endpoint also works" disclaimers.
- **(D)** Operator dashboards stop needing the v1-usage tile
  (the LegacyV1Stats endpoint surfaces who's hitting v1) —
  there is no v1 to hit.
- **(W)** Operator docs get *one* update: "v1 is supported only
  if you opt in via `:LegacyV1Config.enabled`; new features land
  on v2." The legacy-stats tile becomes more important, not less.

### §3h CI simplification

- **(D)** `V5WireFidelityIT` and friends go away — ~1,196 LoC of
  test removed from CI. Estimated build-time saving: 2–4 minutes
  per `mvn verify`. (Reproducible: time `mvn -pl backend
  -Dtest='*V5WireFidelityIT*' verify` against `mvn -pl backend
  verify`.)
- **(D)** SpotBugs / findsecbugs stops scanning ~6,200 LoC of
  v1 REST code — secondary; not the bulk of the SpotBugs run.
- **(D)** OpenAPI generator job for the TS client goes away.
- **(W)** No CI saving.

---

## §4 What we'd lose

The honest cost side. Each item is labelled with whether (W) or
(D) pays it.

### §4a Operator upgrade-from-upstream story

- **(D)** `aidocs/34`'s explicit pitch ("`/shepard/api/...` works
  exactly like upstream — zero breakage") goes away. An operator
  considering switching from upstream-`5.2.0` to this fork's
  `main` loses the zero-breakage promise. The pitch becomes "v2
  is a superset of v1's *capabilities* but a different wire
  surface; budget for a client migration."
- **(W)** No loss — the pitch holds.

### §4b Existing clients

The operator must research this. What we **know**:

- The DLR Augsburg (ZLP) MFFD pipeline uses both v1 and v2 paths
  per `project_mffd_import_workflow.md` — the `mffd-dropbox-import.py`
  ingestion uses `appId` extensively per `feedback_appid_to_shepardid.md`.
- The Helmholtz Unhide harvest integration (`aidocs/integrations/67`)
  is plugin-shaped and uses v2.
- `dataship` (the DLR data import tool) — usage TBD.
- `instdlr` — usage TBD.
- The MCP server uses v2 endpoints (per `project_mcp_path.md`).
- The shepard-admin CLI uses v2 admin endpoints (per CLAUDE.md
  "admin knob" rule).

What we **don't know** and the operator must establish:

- Is **any** production client running outside our control still
  calling `/shepard/api/...`?
- Specifically: does DLR Lampoldshausen (LUMEN — see
  `project_lumen_is_damast.md`) have a client? Do any of the DLR
  partner institutes that adopted upstream-`5.2.0` still expect
  the wire shape?
- The marker plugin's deprecation stats (LegacyV1StatsService)
  collect this on every deployment that runs the plugin. Reading
  those stats off the production deployments is the literal
  answer.

This is the §6 trigger point.

- **(D)** Every client built against upstream-`5.2.0` OpenAPI
  breaks at the upgrade boundary. Migration burden: regenerate
  client against v2 OpenAPI; rewrite call sites where the v1↔v2
  shape changed (most calls are mechanical, ~10–20% need real
  thought per the L2 chain's wire-shape comparisons).
- **(W)** No client breaks. Operators leave `:LegacyV1Config.enabled`
  at `true` and the wire is identical to today.

### §4c Documentation churn

- **(D)** `aidocs/34` chapter goes away; new "v2-only" chapter
  replaces it. `docs/reference/v1-deprecation.md` is rewritten
  as a historical note pointing at the deletion commit. Help
  docs that reference v1 paths get search-and-replaced (~30 hits
  per a `docs/` grep on `/shepard/api/`).
- **(W)** Minimal — one row in `aidocs/34` saying "v1 is now in
  hands-off maintenance mode; new code is v2-only."

### §4d Upstream divergence

- **(D)** Accepting v1-drop confirms we are now structurally a
  fork that will not merge back upstream — at least not on the
  REST surface. The plugin model, the SPI seams, the L2 chain,
  the SHACL substrate, the appId/shepardId namespace — all of
  these are already structurally fork-only by 2026-05-31. v1
  drop is the **explicit announcement** of what is already
  *de facto* the case.
- **(W)** Same divergence trajectory but no explicit gesture.
  Upstream maintainers could in principle still cherry-pick v2
  endpoints into upstream; the v1 contract stays compatible.

This is "pro AND con." Pro: declaring fork-status removes
ambiguity, lets the project commit fully to its design path. Con:
forecloses the optionality of merging back, even if that
optionality is mostly theoretical by now.

---

## §5 Migration shapes

Three options. Each is described against (W) and (D); the
recommendation in §7 picks one.

### §5a Big bang — coordinated breaking release

Cut a final tagged release with v1 still active. Next major
release drops v1. Operators get one deprecation window.

Timeline (90 days):

| Day | Milestone |
|---|---|
| 0 | Cut `v6.0.0` with `:LegacyV1Config.enabled = true` default; deprecation banner activated; operator advisory shipped (mail + GitHub release notes + `aidocs/34` row). Begin LegacyV1StatsService data collection across deployments. |
| 30 | Mid-window check: pull LegacyV1Stats from all known deployments. Identify any client still hitting v1 paths. Reach out individually. |
| 60 | Final advisory: "v7.0.0 in 30 days will not serve `/shepard/api/...`. Migrate now or pin to v6 LTS." |
| 90 | Cut `v7.0.0`. Backend deletion lands. Frontend v1 client deletion lands. Backend-client npm package marked deprecated. `v6.x` becomes an LTS branch for 12 months. |

Cost: one coordinated release-engineering pass; an LTS branch to
maintain for 12 months; one set of operator advisories.

Trigger: this is the right shape if (a) we've verified no production
client depends on v1, OR (b) we're prepared to break the ones that
do at a coordinated gate.

### §5b Per-feature retirement — wire-freeze with attrition

v1 endpoints retire as their v2 sister stabilises. Track in
`aidocs/34`. Each retirement gets its own operator-runbook entry.
The marker plugin stays as the v1 surface kill-switch; individual
endpoints inside the plugin go dark over time.

Timeline (rolling):

| Phase | Scope |
|---|---|
| **Now** | Declare wire-freeze policy: contributor mainline is v2-only; new endpoints land on `/v2/...`. v1 surface gets bug-fixes + security-fixes only. Capture in `CLAUDE.md` + `aidocs/34`. |
| **Wave 1 (1-2 months)** | Migrate the 53 frontend composables off `useShepardApi` onto `useV2ShepardApi`. BUG-COLL-APPID-ROUTE-002 fix lives here. The `@dlr-shepard/backend-client` package stays publishable but stops being a frontend dependency. |
| **Wave 2 (3-6 months)** | Coordinate with the `appId → shepardId` rename (Phase 1 + Phase 2). Since contributor mainline is v2-only by now, the rename is straight-line on v2; v1 takes the additive-getter pattern only where it must. |
| **Wave 3 (6-12 months)** | Per-endpoint retirement starts. Each v1 endpoint with a stable v2 sister gets a 6-month per-endpoint deprecation window: Sunset header on, audit-log per call, LegacyV1Stats-tracked. Operators flip-off endpoints individually. |
| **Long tail** | A subset of v1 endpoints may never retire — the upstream-byte-fidelity guarantee for the remaining surface lives in the marker plugin indefinitely. |

Cost: ongoing but small. No coordinated release-engineering event.

Trigger: this is the right shape if (a) we have *some* clients still
on v1 but they're not blocking, AND (b) we want the cognitive-load
saving without the operator-side disruption.

### §5c Per-operator runtime toggle — current strategy

`project_v1_sunset_strategy.md` (2026-05-22): "no fork-imposed
sunset; per-operator runtime toggle; plugin stays in the codebase
indefinitely as a maintained module — every fork release keeps it
green."

This is the *do nothing new* option. The marker plugin is shipped;
`:LegacyV1Config.enabled` is the operator's lever; the LegacyV1Stats
service tells them when their tools have stopped calling v1; they
flip the toggle when they're ready.

Timeline (forever):

| Phase | Scope |
|---|---|
| Default | Plugin enabled. v1 works. New code lands wherever the contributor put it (no wire-freeze rule). |
| Operator-trigger | When the operator decides their tools have migrated, they flip the toggle to `false`. v1 returns HTTP 410. |

Cost: cognitive-load tax stays. Contributor mainline keeps shipping
dual-surface bugs (BUG-COLL-APPID-ROUTE pattern). `appId → shepardId`
stays two-phase.

Trigger: this is the right shape if (a) the contributor tax is
acceptable, AND (b) we have evidence of production clients we want
not to break.

---

## §6 Decision criteria

The operator must establish:

1. **Are there any clients we know of in production calling
   `/shepard/api/...`?**
   The marker plugin's LegacyV1StatsService has been collecting
   exactly this data since V1COMPAT.0 shipped (`d8f6615bf` /
   `4be6880ce` era). Pull stats from the nuclide deployment and
   from any DLR deployment we can reach. If the answer is "no v1
   calls in 30 days from any non-frontend caller," the case for
   (D) strengthens enormously.
2. **Does DLR Augsburg / Lampoldshausen actually use upstream?**
   `project_mffd_api_keys.md` confirms two API keys for MFFD (one
   nuclide, one DLR intranet). What surface do the import tools
   call? The `mffd-dropbox-import.py` work already uses `appId`
   so it's on v2-ish paths. Confirm explicitly with the operator.
3. **Is the contributor-experience cost of dual-surface actually
   shipping bugs?**
   Yes. BUG-COLL-APPID-ROUTE-002 is live, 2026-05-31. The L2c
   partial state shows it accreting. The `shepardId` rename
   two-phase is the cleanest evidence — the work *splits because
   of v1*. The contributor tax is concrete, not hypothetical.
4. **Does the `shepardId` rename land first, or is it gated on
   dropping v1?**
   Today it's split into two phases *because of* v1. Under (D)
   it's one phase. The interesting question: do we ship the
   rename in dual-phase shape (and accept that the rename
   itself becomes a slow-rolling cost) or do we make the rename
   gate on the v1 decision?
   - If (D) happens fast (≤ 3 months), the rename benefits
     directly — collapses to one phase.
   - If (D) happens slowly (12+ months), the rename can't wait;
     ships in the two-phase shape; (D) becomes only a CI/code
     simplification, not a rename simplification.
5. **Is there a "scratch test" operator signal we can read?**
   Yes: `LegacyV1StatsService` itself. The endpoint
   `GET /v2/admin/legacy/v1/stats` (or its CLI equivalent) returns
   "how many v1 calls in the last 30 days, by endpoint." If that
   answer is dominated by "frontend composables" (i.e. our own
   first-party code), the case for (D) is concrete. If it's
   dominated by API-key-authed external callers, that's the §6.1
   client list to research.

---

## §7 Recommendation

**Adopt `5b` (per-feature retirement) layered on `5c` (keep the
runtime toggle).**

Concretely:

1. **Today.** Declare the **wire-freeze policy** in `CLAUDE.md`.
   The new rule:
   > New endpoints land on `/v2/`. v1 (`/shepard/api/...`) is in
   > maintenance — bug-fixes and security-fixes only. No new IO
   > shapes enter `BasicEntityIO`; new fields go on v2 subclasses
   > directly. The contributor mainline reasons about v2 only.
   This is the (W) gesture. It pays §3a (limited), §3b (substantial),
   §3c (none), §3d (limited), §3e (**the big one — full**), §3f
   (limited), §3g (limited), §3h (none).
2. **Wave 1 — frontend migration.** Migrate the 53 v1-consuming
   composables to v2. This is the BUG-COLL-APPID-ROUTE-002 fix.
   The `@dlr-shepard/backend-client` package stays publishable for
   external clients but stops being a frontend dependency. Tracked
   as `V1-SUNSET-05/06` in §8.
3. **Wave 2 — `shepardId` rename in v2-only shape.** Phase 1 of
   the rename (PR #58) ships against v2 only. Phase 2 (PR #123)
   becomes much simpler because `BasicEntityIO` can be renamed
   directly (the v1 IOs that inherited it get either left at
   `appId` via additive `@JsonProperty` or migrated to a frozen
   `LegacyBasicEntityIO` in the plugin module). Calendar saving:
   1-2 months.
4. **Wave 3 — operator data gathering.** 6 months of
   LegacyV1StatsService data. Confirm or deny external v1 clients.
   At end-of-window the operator decides whether to escalate to
   (D) or stay at (W).
5. **(D) deferred.** The runtime toggle stays available. Operators
   who never need v1 can disable it today. Operators who do can
   keep using it. The fork-vs-upstream split (`aidocs/34`'s
   zero-breakage promise) stays intact for the operators who
   want it.

**Why not 5a (big bang).** Without operator-side trigger evidence
("we know X clients still call v1, we have a plan for them"), a
big-bang gesture contradicts the explicit 2026-05-22 policy
(`project_v1_sunset_strategy.md`) without paying for the
reversal. The cost (operator advisory + LTS branch maintenance) is
front-loaded; the benefit (deletion) is identical to per-feature
retirement, just compressed. Compression has no operator value
*unless* there's a forcing function we don't see.

**Why not 5c alone (status quo).** The contributor tax is concrete
and ongoing. The `shepardId` rename is split into two phases
*because of v1*; the L2c partial state stays partial; new
contributors learn the additive-`@JsonProperty` pattern. Status
quo keeps shipping BUG-COLL-APPID-ROUTE patterns. Doing nothing
costs.

**The pick is (W) plus a deferred (D) decision** — drop v1 from
the contributor mainline immediately, keep it operationally
available indefinitely, escalate to deletion when the operator has
the data to do so safely.

---

## §8 If we do drop v1 — implementation sub-rows for `aidocs/16`

Paste-ready table for `aidocs/16-dispatcher-backlog.md`. The phases
assume the §7 recommendation: V1-SUNSET-01..06 are the (W) work
(safe to start now); V1-SUNSET-07..08 are the (D) work (gated on
the §6 data-gathering window).

```markdown
| **V1-SUNSET-01-DESIGN-DOC** | This design doc (`aidocs/platform/112-v1-sunset-gains-design.md`). Establishes the wire-freeze policy + per-feature retirement strategy. | XS | **done (2026-05-31)** | Frame for V1-SUNSET-02..08. |
| **V1-SUNSET-02-WIRE-FREEZE-CLAUDE-MD** | Add the wire-freeze rule to `CLAUDE.md` under "API-version policy": new endpoints land on `/v2/`; no new IO shapes enter `BasicEntityIO`; v1 surface in maintenance mode. Update `aidocs/34` "API-version policy" chapter to reflect the freeze. | XS | queued | The doctrinal landing pad. Cheap; enables everything below. |
| **V1-SUNSET-03-LEGACYV1STATS-DASHBOARD** | Surface the LegacyV1StatsService data in the admin UI + via a `shepard-admin legacy v1 stats` CLI command. The data already collects per V1COMPAT.0; this row is the read-side surfacing. Gating evidence for V1-SUNSET-07. | S | queued | Pairs with the marker plugin's stats service. |
| **V1-SUNSET-04-INVENTORY** | Full inventory pass: every v1 REST endpoint's v2 sister (or absence-of-sister). Identify v1 endpoints with no v2 equivalent (these are the ones that must ship a v2 path before the v1 path can deprecate). Output a table in `aidocs/platform/112-appendix.md`. | M | queued | Sub-row of V1-SUNSET-02. |
| **V1-SUNSET-05-MIGRATE-COMPOSABLES** | Migrate the 53 frontend composables consuming `useShepardApi` to `useV2ShepardApi`. Per-composable PRs grouped by domain (collections/dataobjects, containers, references, semantic, admin). Includes BUG-COLL-APPID-ROUTE-002 fix as a downstream effect. | L | queued | The big one. Estimated 4-6 weeks if dispatched in waves. |
| **V1-SUNSET-06-DROP-FRONTEND-V1-CLIENT** | After V1-SUNSET-05, drop the `@dlr-shepard/backend-client` dependency from `frontend/package.json`; delete `useShepardApi.ts`; the npm package stays publishable for external consumers. | S | queued | Triggered by V1-SUNSET-05 completion. |
| **V1-SUNSET-07-DELETE-BACKEND-V1-REST** | (D) gesture. Delete 29 `*Rest.java` files + supporting IO classes + 12 wirefidelity ITs + 4 support classes + the `plugins/v1-compat/` plugin + `:LegacyV1Config` singleton + V63 migration (with a paired V##_R__ rollback). Coordinated with a major version bump (v7.0.0). | XL | **gated** | Gated on §6 evidence: `LegacyV1StatsService` shows zero external-client traffic for 30+ days; operator confirms no DLR client requires v1. |
| **V1-SUNSET-08-DOCS-RENAME** | Rewrite `aidocs/34`'s "API-version policy" chapter post-(D); update `docs/admin/` to drop v1 mentions; reframe `aidocs/platform/103` + `103a` as archived design docs (move under `aidocs/archive/`). | M | **gated** | Sub-row of V1-SUNSET-07. |
```

Row coordination notes:

- V1-SUNSET-02 lands first; it's the doctrinal change that unblocks
  the wave.
- V1-SUNSET-05 is the biggest single cost-saver — BUG-COLL-APPID-ROUTE-002
  resolves as a side effect, the cognitive-load tax drops, and
  the frontend stops being the source of dual-surface bugs.
- V1-SUNSET-07 and V1-SUNSET-08 are explicitly gated. They do not
  ship without §6 evidence. The standing policy stays "per-operator
  runtime toggle, no forced sunset" until and unless that gate
  flips.
- The `appId → shepardId` rename (PR #58 + #123) does **not** wait
  on V1-SUNSET-*. It ships in its current two-phase shape, but
  the second phase becomes much simpler if V1-SUNSET-07 happens
  before #123 (Java-internal rename pass) is scheduled.

---

## §9 Open questions for operator decision

Resolve before V1-SUNSET-02 lands:

1. **Big-bang vs per-feature vs status-quo?** The doc recommends
   per-feature (`5b`) layered on status-quo (`5c`). Confirm or
   pick different.
2. **Coordinate the `shepardId` rename with the v1 drop, or
   keep separate?** Recommendation: keep separate. The rename
   ships in its current two-phase shape; the v1 drop happens
   independently. If V1-SUNSET-07 lands first, Phase 2 of the
   rename simplifies; if not, the rename pays the (W) tax. Both
   work.
3. **Communicate the wire-freeze to upstream maintainers as a
   courtesy?** Yes — a single GitHub Issue on the upstream
   `gitlab.com/dlr-shepard/shepard` repository announcing the
   policy. The wire stays compatible; the doctrinal split is the
   announcement.
4. **Deprecation window length for V1-SUNSET-07 (if it triggers)?**
   Kubernetes API deprecation policy: beta APIs supported 9 months
   / 3 releases. RFC 9745 + RFC 8594 (`Deprecation` + `Sunset`
   headers) are the standards. Recommendation: 12 months of
   wire-freeze + LegacyV1Stats data + Sunset-header response, then
   30-day final advisory, then deletion in the next major version.
5. **Cut a "v6.x" LTS release for operators who want to stay on
   v1?** Recommendation: yes — if V1-SUNSET-07 ever triggers,
   ship a v6.x LTS branch with security fixes only for 12 months
   post-deletion. The marker plugin stays maintained on that
   branch.
6. **Marker plugin's future under (W) recommendation:** Phase 2
   extraction (`aidocs/platform/103`) becomes architectural
   cleanup rather than sunset preparation. Operator decision:
   ship Phase 2 because it's architecturally cleaner, or defer
   indefinitely because the marker-plugin shape is sufficient?
   Recommendation: defer until V1-SUNSET-07 triggers — extraction
   pays off most when deletion is imminent.

---

## §10 Persona-board lens

**Reluctant Senior Researcher.** Never knew there was a v1. Has
never typed `/shepard/api/` in his life. Reads the wire-freeze
announcement: "I don't know what any of this means. Will my data
still work? Will the people I told to use the data still be able
to use the data?" *(Yes, no change visible to him.)* Verdict: he
doesn't care. The decision is invisible to him under both (W) and
(D). The only thing he'd notice is if (D) breaks one of his
collaborators' tools — which is exactly the §6.1 evidence
gate.

**Digital Native.** Opens the API docs, sees there are *two*
shelves: `/shepard/api/` and `/v2/`. Writes "WTF why are there two"
in his notebook. Reads the wire-freeze announcement: "OK, v2 only.
Got it. Why is the other one still there?" Migrates his Jupyter
notebooks to v2 in an afternoon. Verdict: cares; wants (D). The
two-surface split is a friction point for new adopters, even when
v1 is stable. The wire-freeze rule (§7 recommendation) is the
half-measure he'd accept; (D) is what he'd advocate.

**Operator.** Cares deeply. The question fundamentally is "can I
keep upgrading?" Under (W): yes, business as usual; new features
land, v1 stays wire-stable. Under (D): the upgrade boundary
breaks for any external client on v1; LTS branch covers them but
adds an ops surface. Verdict: prefers (W) for the wire-freeze
benefit (contributor velocity goes up, fewer dual-surface bugs)
without the operational surface of running an LTS branch. (D) only
if the LegacyV1Stats data shows zero external usage.

**Upstream maintainer.** Reads the wire-freeze announcement as a
courtesy: "interesting — they're explicitly declaring fork-status
on the REST surface. The wire stays compatible, so my clients
still work against their fork if they want to switch. But new
features only flow one way now (their fork → can't pick back to
upstream). Forks-fork." Verdict: cares mildly; appreciates the
courtesy of the explicit declaration; treats it as confirmation
of what was de facto already true. (D) would be a stronger signal
but the wire-freeze + plugin shape is already the structural
declaration.

---

## Appendix — Counts reproduced 2026-05-31

```
$ find backend/src/main/java/de/dlr/shepard -name "*.java" \
    -not -path "*/v2/*" | xargs grep -l "SHEPARD_API" 2>/dev/null | wc -l
29

$ find backend/src/main/java/de/dlr/shepard -name "*Rest.java" \
    -not -path "*/v2/*" | xargs wc -l | tail -1
6174 total

$ find backend/src/main/java/de/dlr/shepard -name "*IO.java" \
    -not -path "*/v2/*" | xargs wc -l | tail -1
2242 total

$ find plugins/v1-compat -name "*.java" | xargs wc -l | tail -1
2923 total

$ find backend/src/test -name "*V5WireFidelity*" -o -name "V5Json*" \
    | xargs wc -l | tail -1
1196 total

$ find backend-client -name "*.ts" | xargs wc -l | tail -1
36779 total

$ grep -rln "useShepardApi" frontend/composables | wc -l
53

$ grep -rln "useV2ShepardApi" frontend/composables | wc -l
26
```

External reading consulted:

- Kubernetes API deprecation policy
  (https://kubernetes.io/docs/reference/using-api/deprecation-policy/)
  — 9-month / 3-release window for beta APIs is the
  industry-standard precedent.
- RFC 9745 (`Deprecation` HTTP header) + RFC 8594 (`Sunset`
  header) — the wire-level standards for communicating
  deprecation to clients. Marker plugin already uses
  `X-Shepard-Legacy`; the standards-compliant pair could replace
  it in V1-SUNSET-02 work.
- Zalando RESTful API Guidelines (deprecation chapter,
  https://github.com/zalando/restful-api-guidelines/blob/main/chapters/deprecation.adoc)
  — phased retirement template.
- 5b's wave structure is modelled on the Kubernetes per-API
  deprecation pattern (each API has its own deprecation lifecycle
  rather than a project-wide cutoff).

---

**End of design.** The operator commits.
