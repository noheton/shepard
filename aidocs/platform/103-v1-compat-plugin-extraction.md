# `shepard-plugin-v1-compat` — design for extracting the
# upstream-v5.2.0 byte-compatible `/shepard/api/...` surface

**Status.** Design, not implementation. Awaits clarifications (see §4 + §5).
**Snapshot date.** 2026-05-22.
**Companion docs.** `CLAUDE.md §"API-version policy"`, `aidocs/34`,
`aidocs/44`, `aidocs/platform/25` (L2 identifier chain),
`aidocs/platform/68-v2-baseline-v1-compat-layer.md` (the prior compat
design — see §0).
**Tracker hooks (no edits yet).** `aidocs/34` row(s) per Phase, `aidocs/44`
status flip for any v1 row that becomes "shipped via plugin", new row
"V1COMPAT" in both ledgers.

---

## 0. Relationship to the prior compat design (read first)

`aidocs/platform/68-v2-baseline-v1-compat-layer.md` already proposes a
different shape for the v1 surface: a Java-package move within the
backend module (`de.dlr.shepard.compat.v1.*Rest`) plus a thin-adapter
posture where `/v2/` is the implementation. **It does NOT propose JAR
extraction.** This doc proposes the next step: pulling that
`compat.v1.*` package out into a `shepard-plugin-v1-compat` JAR.

`[NEEDS-CLARIFICATION] 0.1` — relationship between this design and #68
> Context: #68 is unimplemented but signed off as a design. This doc
> either supersedes, sequences after, or coexists with #68.
> Options:
>   A) **Sequence**: #68 lands first (Phase A package move + Phase B
>      adapter extraction); this doc is "Phase D" — the JAR boundary.
>      Pro: each phase has small blast radius; #68's Phase A is a
>      zero-risk rename that pre-positions code for extraction.
>      Con: 3 to 6 months of in-tree work before the plugin ships.
>   B) **Supersede**: archive #68; do package-move + JAR-extraction in
>      one PR series under this design. Pro: half the calendar time.
>      Con: bigger PRs, more risk per landing.
>   C) **Coexist**: #68 lands for the in-tree compat layer; this design
>      becomes optional "operators who want to drop v1 entirely" path.
>      Pro: optional cost. Con: two compat layers, double maintenance.
> Lean: **A** — #68's Phase A (pure package rename to
> `de.dlr.shepard.compat.v1.*`) is the natural precursor to extraction
> and de-risks the JAR-boundary work. Phase B (adapter extraction)
> resolves the shared-IO problem (§4.1) in tree, where the diff is
> reviewable, before the plugin boundary makes it harder.

If clarification 0.1 = B or C, several decisions below shift; flagged
inline.

---

## 1. Why — the constraint analysis

**Today.** Every PR that touches a `*Rest.java` under
`de.dlr.shepard.{context,data,auth,common}/.../endpoints/` or any IO
class in the inheritance chain rooted at
`common/neo4j/io/BasicEntityIO.java` must reason about v1 wire compat.
Concrete tax:

| Signal | Count (2026-05-22) |
|---|---|
| `*Rest.java` files under v1 packages (non-`v2.*`) | **29** |
| `@Path(Constants.SHEPARD_API + …)` call sites | **15** root paths (sub-paths drive the actual endpoint count up well past 100) |
| IO classes outside `v2.*` (the "v1 + shared" surface) | **37** |
| IO classes in `v2.*` (mostly thin wrappers) | **79** — but many import from the 37 above |
| Test files referencing `shepard/api` or `SHEPARD_API` | **21** |
| v5 fixture files (the regression contract) | **10** under `backend/src/test/resources/fixtures/v5/` |
| `Constants.SHEPARD_API` Java references | **29** files |
| Recent PR that paid this tax | shepardId rename (commit `b943b1c5` introduced `V1WireFidelityTest`) and PR series referenced in `feedback_appid_to_shepardid.md` — every PR 3–10 in that sweep had to keep `appId` on `BasicEntityIO` |

**Tomorrow.** Core dev mainstream is `/v2/` + admin + services + storage
+ migrations. The plugin's job is the upstream wire freeze. Future PRs
touching `/v2/` ignore v1 by construction; only PRs that explicitly
change the plugin module reason about byte-fidelity.

**Honest framing.** This is the LARGEST plugin in the tree. Every other
`shepard-plugin-*` ships one payload kind or one integration. This
plugin ships an entire API shelf — ~29 REST classes + their IOs + a
fixture corpus + a regression test suite. It is the structural fix for
the standing "v1 wire freeze" rule in CLAUDE.md, not a feature plugin.

---

## 2. Scope of v1 surface — endpoint inventory

Built from the 15 root `@Path(SHEPARD_API + …)` declarations. The
"shared services" column shows the in-tree services the plugin will
depend on (one-way dependency: plugin → core; never reverse).

| Root path | Java file (today) | IO classes returned | Shared services consumed |
|---|---|---|---|
| `/shepard/api/collections` | `context/collection/endpoints/CollectionRest.java` | `CollectionIO`, `PermissionsIO` | `CollectionService`, `ExportService`, `PermissionsService` |
| `/shepard/api/collections/{id}/dataObjects` | `context/collection/endpoints/DataObjectRest.java` | `DataObjectIO` | `DataObjectService` |
| `/shepard/api/collections` (versioning) | `context/collection/endpoints/CollectionVersioningRest.java` | `VersionIO` | `CollectionVersioningService` |
| `/shepard/api/labJournalEntries` | `context/labJournal/endpoints/LabJournalEntryRest.java` | `LabJournalEntryIO` | `LabJournalService` |
| `/shepard/api/dataObjects/{id}/.../semanticAnnotations` | `context/semantic/endpoints/DataObjectSemanticAnnotationRest.java` | `SemanticAnnotationIO` | `SemanticService` |
| `/shepard/api/basicReferences/.../semanticAnnotations` | `BasicReferenceSemanticAnnotationRest.java` | `SemanticAnnotationIO` | `SemanticService` |
| `/shepard/api/collections/{id}/semanticAnnotations` | `CollectionSemanticAnnotationRest.java` | `SemanticAnnotationIO` | `SemanticService` |
| `/shepard/api/timeseriesContainers/.../semanticAnnotations` | `AnnotatableTimeseriesRest.java` | `SemanticAnnotationIO` | `SemanticService` |
| `/shepard/api/semanticRepositories` | `SemanticRepositoryRest.java` | `SemanticRepositoryIO` | `SemanticRepositoryService` |
| `/shepard/api/.../uriReferences` | `context/references/uri/endpoints/URIReferenceRest.java` | `URIReferenceIO` | `URIReferenceService` |
| `/shepard/api/.../fileReferences` | `FileReferenceRest.java` | `FileReferenceIO`, `FileGroupIO` | `FileReferenceService`, `FileService` |
| `/shepard/api/.../structuredDataReferences` | `StructuredDataReferenceRest.java` | `StructuredDataReferenceIO` | `StructuredDataReferenceService` |
| `/shepard/api/.../timeseriesReferences` | `TimeseriesReferenceRest.java` + `TimeseriesReferenceMetricsRest.java` | `TimeseriesReferenceIO`, `MetricsIO` | `TimeseriesReferenceService`, `TimeseriesService` |
| `/shepard/api/.../dataObjectReferences` | `DataObjectReferenceRest.java` | `DataObjectReferenceIO` | `DataObjectReferenceService` |
| `/shepard/api/.../collectionReferences` | `CollectionReferenceRest.java` | `CollectionReferenceIO` | `CollectionReferenceService` |
| `/shepard/api/.../basicReferences` | `BasicReferenceRest.java` | `BasicReferenceIO` | `BasicReferenceService` |
| `/shepard/api/users` | `auth/users/endpoints/UserRest.java` | `UserIO` | `UserService` |
| `/shepard/api/users/.../apiKeys` | `auth/apikey/endpoints/ApiKeyRest.java` | `ApiKeyIO`, `ApiKeyWithJWTIO` | `ApiKeyService` |
| `/shepard/api/users/.../subscriptions` | `common/subscription/endpoints/SubscriptionRest.java` | `SubscriptionIO`, `EventIO` | `SubscriptionService` |
| `/shepard/api/userGroups` | `auth/users/endpoints/UserGroupRest.java` | `UserGroupIO`, `PermissionsIO` | `UserGroupService` |
| `/shepard/api/fileContainers` | `data/file/endpoints/FileRest.java` | `FileContainerIO` | `FileService`, `FileStorage` SPI |
| `/shepard/api/structuredDataContainers` | `data/structureddata/endpoints/StructuredDataRest.java` | `StructuredDataContainerIO` | `StructuredDataService` |
| `/shepard/api/timeseriesContainers` | `data/timeseries/endpoints/TimeseriesRest.java` | `TimeseriesContainerIO`, `TimeseriesIO` | `TimeseriesService`, `TimeseriesStorage` |
| `/shepard/api/temp/migrations` | `data/timeseries/migration/endpoints/MigrationProgressRest.java` | `MigrationProgressIO` | `TimeseriesMigrationService` |
| `/shepard/api/search` | `common/search/endpoints/SearchRest.java` | search result wrappers | `SearchService` |
| `/shepard/api/versionz` | `common/versionz/VersionzRest.java` | `VersionzIO` | (none — reads build info) |

**29 REST classes total** (count includes a few sub-resources not
in the table). **37 IO classes** in v1-or-shared packages.

`[NEEDS-CLARIFICATION] 2.1` — `temp/migrations` endpoint
> Context: `MigrationProgressRest` is at `/shepard/api/temp/migrations`.
> The `temp/` prefix suggests it was never meant to be permanent. Was
> it part of upstream 5.2.0?
> Options:
>   A) Yes, upstream surface → ships in plugin.
>   B) No, fork addition under `/shepard/api/` (against policy) → move
>      to `/v2/admin/timeseries-migration` before extraction; do NOT
>      ship in plugin.
>   C) Yes upstream, but already deprecated → ship in plugin with
>      `@Deprecated` log line from day one.
> Lean: **B** — `temp/` is almost certainly a fork addition. Check
> `aidocs/34` ledger for L2c/L2d rows.

---

## 3. Architecture target

```
┌─────────────────────────────────────────────────────────────┐
│ LAYER 3 — Operator surface                                  │
│   • shepard.compat.v1.enabled (default true)                │
│   • GET/PATCH /v2/admin/legacy/v1 (:V1CompatConfig)         │
│   • Frontend banner: "v1 API in deprecated mode"            │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────┐
│ LAYER 2 — `shepard-plugin-v1-compat` (the JAR)              │
│   • 29 *Rest classes (formerly de.dlr.shepard.compat.v1.*)  │
│   • V1-only IO classes (per shared-IO decision §4.1)        │
│   • v5 fixture corpus + V5WireFidelityTest subclasses       │
│   • DeprecationLogFilter (logs every /shepard/api/* hit)    │
│   • V1CompatPluginManifest implements PluginManifest        │
│   • Pulled into backend.jar via `with-plugins` profile      │
│   • Discovered by Quarkus build-time CDI scan (no new SPI)  │
└─────────────────────────────────────────────────────────────┘
                              │  one-way: plugin imports core
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ LAYER 1 — Core backend                                      │
│   • Entities (Neo4j OGM) + services + DAOs + storage SPIs   │
│   • `/v2/...` REST + `/v2/admin/...`                        │
│   • Common: BasicEntity, BasicEntityIO (if kept here)       │
│   • Migrations runner, auth (JWT, OIDC), permissions,       │
│     plugin SPI registry, provenance capture filter          │
└─────────────────────────────────────────────────────────────┘
```

**Mechanism (not unusual).** The plugin uses the same Quarkus CDI
classpath-scan pattern that `shepard-plugin-aas`,
`shepard-plugin-minter-datacite`, and others already use. JAX-RS
`@Path` resources in the plugin's classpath are auto-registered. No
new SPI is needed — the v1 compat plugin is unusual in **size** (29
REST classes, ~37 IOs, full fixture corpus), not in **mechanism**.

**Dependency direction.** Strictly plugin → core. Core never imports
plugin. Plugin compiles against core in `provided` scope, like every
other shepard plugin (per `plugins/file-s3/pom.xml`).

---

## 4. The four hard architectural decisions

### 4.1 Shared-IO strategy — the single biggest fork

The v1 wire freeze contract is enforced by the JSON shape of ~37 IO
classes. Grep confirms these are **shared between v1 and v2**:

- `BasicEntityIO` — base for nearly everything
- `CollectionIO`, `DataObjectIO` — returned by both
  `CollectionRest.java` (v1) AND `v2/collection/.../CollectionV2Rest`
- `FileReferenceIO`, `TimeseriesReferenceIO`,
  `StructuredDataReferenceIO`, `BasicReferenceIO`,
  `DataObjectReferenceIO`, `CollectionReferenceIO`, `URIReferenceIO`
- `FileContainerIO`, `TimeseriesContainerIO`,
  `StructuredDataContainerIO`
- `UserIO`, `UserGroupIO`, `PermissionsIO`, `ApiKeyIO`
- `SemanticAnnotationIO`, `SemanticRepositoryIO`, `LabJournalEntryIO`

Only a handful of v2 IOs are wire-distinct today:
`DataObjectDetailV2IO`, `DataObjectListItemV2IO`, `ImportManifestIO`,
output-profile IOs, admin IOs. **The dual-use IO is the dominant
shape, not the exception.**

`[NEEDS-CLARIFICATION] 4.1` — where do shared IO classes live after extraction?
> Context: today both `/shepard/api/` and `/v2/` endpoints return the
> SAME IO classes. The plugin can't own them (core needs them); core
> can't own them and still call it "v1-extracted".
> Options:
>   A) **Lift base, both inherit, plugin owns v1 leaf IOs.**
>      `BasicEntityIO` (+ `AbstractDataObjectIO`, `BasicContainerIO`,
>      `BasicReferenceIO`) stay in core. v2 endpoints get
>      `v2/CollectionIO` (new, in core). v1 endpoints get the existing
>      `CollectionIO` MOVED into plugin. ~30 IO files move.
>      Pro: clean wire-shape ownership. Plugin owns wire freeze.
>      Con: 30 IO-class duplications + every v2 endpoint that today
>      returns `CollectionIO` must switch to `v2.CollectionIO` — that
>      is itself a wire change on v2. (Acceptable per CLAUDE.md but
>      adopters of v2 see a churn.)
>   B) **Duplicate base classes per surface.** Plugin owns
>      `V1BasicEntityIO` + `V1CollectionIO`; core owns `V2BasicEntityIO`
>      + `CollectionIO`. Conversion via mappers in the plugin's adapter
>      layer. Pro: zero coupling on the wire shape — the strongest
>      end-state. Con: biggest one-time cost (~67 IO classes touched,
>      mapper layer to write, ~5–8 KLOC); the test matrix gets ugly.
>   C) **Inverse — v2 inherits from v1's; plugin must be present
>      always.** Defeats the purpose; rejected.
>   D) **Keep BasicEntityIO + leaf IOs in core; plugin overrides wire
>      via Jackson `MixIn` annotations.** The plugin registers a
>      Jackson `Module` that re-skins shared IOs to v1 shape on the
>      v1 endpoints (e.g. strip `shepardId`, keep only `id` + `appId`).
>      Pro: zero IO duplication; one mixin per surface. Con:
>      MessageBodyWriter resolution per-resource-class is non-trivial
>      in JAX-RS; risk of leakage if a v2 endpoint accidentally picks
>      up the v1-mode `ObjectMapper`.
>   E) **Inventory-driven hybrid.** For each of the 37 IO classes,
>      decide individually based on whether v2 will diverge soon:
>      - Pure v1 (no v2 caller): MOVE to plugin (option A locally).
>      - Truly shared, v2 unlikely to diverge: KEEP in core, plugin
>        imports from core (option D locally — Jackson mixin only if
>        a divergence appears later).
>      - Truly shared, v2 already on a divergence path: DUPLICATE
>        (option B locally).
>      Requires a one-PR inventory spike: walk every v2 Rest class,
>      record which v1 IOs it currently returns, and tag each as
>      pure-v1 / share-stable / share-volatile.
> Lean: **E — inventory-driven hybrid** with default to "keep in core,
> plugin imports" (cheapest), upgrade to mixin or duplicate only where
> the inventory shows v2 is already diverging. The shepardId rename
> already proved the BasicEntityIO-shared pattern works without
> wire damage; that pattern (additive `@JsonProperty` on v2 subclass,
> never mutate base) extends to most of these classes. **The inventory
> spike is non-negotiable** — without it, this design is guessing.

This is the largest single decision in the doc and gates ~70% of the
implementation effort.

### 4.2 Default state at extraction

`[NEEDS-CLARIFICATION] 4.2` — does the plugin ship in the default build?
> Context: extraction must not change any v1 response on day one (rule
> #6 in the brief). But the operator-facing knob (deprecation) matters
> for the plugin's purpose.
> Options:
>   A) **Enabled by default — present in default build artifact,
>      compiled into image.** Identical to today's behaviour from an
>      operator's perspective. Pro: zero disruption; honours the v1
>      freeze; deprecation slow and friendly. Con: deprecation is
>      easy to ignore.
>   B) **Disabled by default — operator opts in.** Pro: forces every
>      v5 adopter to consciously turn v1 back on; aggressive
>      deprecation. Con: SILENTLY breaks every existing upstream
>      client on a routine version bump — violates `aidocs/34`
>      "pull-and-restart" promise.
>   C) **Auto-detected — enabled if any `/shepard/api/*` call in the
>      last 30 days (via :Activity log).** Smart but mysterious;
>      operators don't trust magic.
> Lean: **A** — the whole point of the plugin is to honour the v1
> freeze. "Default build" means the plugin JAR is in `with-plugins`
> profile (active by default). Operators who want the leaner image
> opt OUT via `-DnoV1Compat` Maven profile or a runtime
> `shepard.compat.v1.enabled=false` flag (returns 404 on every
> `/shepard/api/*` path with an RFC 7807 body pointing at `/v2/`).

### 4.3 Deprecation timeline

`[NEEDS-CLARIFICATION] 4.3` — when does the v1 surface go away?
> Context: the plugin makes deprecation a knob; the question is what
> the published lifecycle is.
> Options:
>   A) **Year-based**: Y0 extract → Y+1 warn-on-use → Y+2 default-off
>      (still installable) → Y+4 remove plugin module entirely. Pro:
>      predictable for ops. Con: years feels slow for a fork.
>   B) **Major-version-aligned**: v6.x extract + warn-on-use →
>      v7.0 default-off (plugin still installable) → v8.0 plugin
>      module removed. Operators upgrade major versions deliberately.
>      Concrete: today is fork v6.x → first plugin landing is v6.N →
>      flip default-off at v7.0 → removal at v8.0.
>   C) **Community-signal**: every adopter migrated to `/v2/` for
>      12 consecutive months (measured via Unhide harvest signature
>      or telemetry opt-in). Pro: respects real users. Con: no
>      adopter telemetry exists today; requires building observability
>      that doesn't exist; unbounded calendar.
> Lean: **B with version markers** — `aidocs/34` already tracks major
> version bumps (the A3c v6.0 bump shipped 2026-05-16). Anchoring
> deprecation to versions matches every other lifecycle policy in
> this fork. Concrete proposal:
>   • **v6.N (this extraction)** — plugin ships, default-on, no
>     behaviour change. Warn-on-use log line.
>   • **v7.0** — plugin still in default build but
>     `shepard.compat.v1.enabled=false` default. Operator flips on
>     deliberately. Loud admin UI banner.
>   • **v8.0** — plugin module physically removed from the
>     `with-plugins` profile. Operators who still need v1 vendor the
>     plugin out-of-tree.
> Year mapping is roughly Y0/Y+1.5/Y+3 given current major-version
> cadence (~18 months).

### 4.4 Granularity — one plugin or many?

`[NEEDS-CLARIFICATION] 4.4` — monolith or per-feature?
> Context: 29 REST classes can be one JAR or ten.
> Options:
>   A) **Monolithic `shepard-plugin-v1-compat`** — one module, all v1
>      endpoints. Pro: one JAR, one fixture corpus, one
>      `V1CompatConfig`, one CI matrix entry. Con: operators who only
>      want some v1 endpoints (e.g. just `/users/` for an LDAP-sync
>      script) can't drop the rest.
>   B) **Per-feature**: `shepard-plugin-v1-collections`,
>      `…-v1-references`, `…-v1-permissions`, `…-v1-data-containers`,
>      `…-v1-users`, `…-v1-semantic`, `…-v1-lab-journal`. Pro:
>      operators stage their migration. Con: 7+ plugin modules; every
>      one depends on the same core services (no real isolation); the
>      `V1CompatConfig` becomes 7 toggles; YAGNI without operator
>      demand.
>   C) **Two-tier**: a core `shepard-plugin-v1-compat` (collections +
>      data + references — the 80% surface) + optional
>      `shepard-plugin-v1-extras` (users + apiKeys + subscriptions +
>      lab journal). Pro: middle ground. Con: arbitrary split line.
> Lean: **A** — group by surface INSIDE the plugin (e.g.
> `plugins/v1-compat/src/main/java/.../collections/`,
> `.../references/`, …) so a future re-split (option B) is a
> directory move, not a refactor. The shared-services dependency
> means per-feature plugins all carry the same core dep anyway.

---

## 5. Other decisions worth raising

### 5.1 v5 fixture corpus location

`[NEEDS-CLARIFICATION] 5.1` — where do `backend/src/test/resources/fixtures/v5/` and `V5WireFidelityTest` live?
> Options:
>   A) **Move into plugin module** (`plugins/v1-compat/src/test/...`).
>      Pro: contract co-located with implementation. Con: a build
>      with `-DnoV1Compat` cannot run wire-fidelity tests at all.
>   B) **Stay in core** (`backend/src/test/resources/fixtures/v5/`).
>      Pro: fidelity tests always run. Con: tests of code that lives
>      in another module — the "test phase routing" friction from
>      task #133 gets worse.
>   C) **Stay in core but no-op gracefully when plugin absent.** A
>      `@EnabledIf` predicate skips each test if
>      `shepard.compat.v1.enabled=false` or the v1 endpoint
>      classes aren't on the classpath. Pro: best of both. Con:
>      "tests silently skipped" is a known anti-pattern; needs a
>      loud aggregate report ("v1 fidelity not validated this run").
>   D) **Stay in a third module** — `tests/v1-fidelity/` — that's a
>      pure-test artefact, depends on plugin + backend, runs in CI
>      always.
> Lean: **C** with the loud-skip guard. The fixture corpus IS the
> contract; moving it into the plugin makes the contract feel
> optional. Skipping it when the plugin is absent is correct
> behaviour (nothing to validate) but the CI summary must flag it.

### 5.2 Service-layer dependency direction

`[NEEDS-CLARIFICATION] 5.2` — does any core code reach into the plugin?
> Context: standard plugin pattern is plugin → core only. The v1 plugin
> is large enough that subtle reverse-deps could sneak in.
> Options:
>   A) **Strict one-way (plugin → core only).** Reject any PR that
>      adds a core → plugin import. Pro: clean architecture.
>      Con: forces some refactors during extraction (e.g. if a core
>      service today returns a v1 IO type, that service must return
>      an internal DTO or v2 IO).
>   B) **One-way with carve-outs** — allow specific core modules
>      (e.g. `de.dlr.shepard.common.openapi`) to introspect plugin
>      classes for OpenAPI doc generation.
>   C) **Bidirectional via SPI** — define a `LegacyApiHook` SPI in
>      core that the plugin implements; core calls back via SPI
>      where needed. YAGNI today; included for completeness.
> Lean: **A** — anything else makes the plugin "in-tree by another
> name". If a service today returns `CollectionIO`, that's already a
> code smell (services should be DTO-neutral); fix it before
> extraction.

### 5.3 `/v2/admin/legacy/v1/*` admin REST shape

`[NEEDS-CLARIFICATION] 5.3` — admin surface for the plugin
> Per CLAUDE.md §"Always: surface operator knobs in the admin config",
> a `:V1CompatConfig` Neo4j singleton + admin REST + CLI parity.
> Options:
>   A) `GET/PATCH /v2/admin/legacy/v1/config` — one knob:
>      `{enabled: bool, deprecationWarn: bool}`. Lean: **A**.
>   B) Per-endpoint-group disable
>      (`{enabled: true, disabledGroups: ["users", "apiKeys"]}`).
>      Couples to option 4.4.B granularity; YAGNI today.
> Lean: **A** — match the existing A3b feature-toggle pattern.

### 5.4 Deprecation logging level

`[NEEDS-CLARIFICATION] 5.4` — how does the plugin log a v1 call?
> Options:
>   A) `INFO` — too quiet; operators don't see it.
>   B) `WARN` — loud per request; can flood logs at telemetry rate
>      (the home-showcase MQTT collector calls
>      `/shepard/api/timeseriesContainers/{id}/payload` continuously).
>   C) **`AUDIT` event** via existing `ProvenanceCaptureFilter`
>      stamping — adds a `prov:wasInvokedVia = shepard:v1Surface`
>      property on each `:Activity`. No new log line. Operators query
>      the audit log for v1 usage stats.
>   D) Hybrid: WARN once per (path, client-id) pair per process
>      lifetime + audit event always.
> Lean: **D** — the audit event is the durable signal (queryable);
> the WARN-once-per-pair stops log flood while still being visible.

### 5.5 Per-endpoint disable

`[NEEDS-CLARIFICATION] 5.5` — operator-level per-endpoint toggle
> See 5.3. Lean: **YAGNI** — no use case named today. Revisit if an
> operator asks for it.

### 5.6 Test phase routing

`[NEEDS-CLARIFICATION] 5.6` — does plugin test suite run via Surefire or Failsafe?
> Per task #133 (test phase routing in plugins): the wire-fidelity
> tests are `@QuarkusIntegrationTest` (Failsafe). The plugin's unit
> tests for adapter logic would be Surefire.
> Options:
>   A) Both, separated by phase as today.
>   B) Pure failsafe — wire fidelity is the only valuable test; unit
>      tests for adapters are low-value.
> Lean: **A** — matches the existing pattern.

### 5.7 OpenAPI doc generation

`[NEEDS-CLARIFICATION] 5.7` — where does the v1 OpenAPI live after extraction?
> Today `OpenApiPerShelfRest` already generates per-shelf docs.
> Options:
>   A) Plugin emits its own `openapi-v1.yaml` resource;
>      `OpenApiPerShelfRest` aggregates at runtime.
>   B) Core continues to generate `openapi-v1.yaml` by introspecting
>      classes on the classpath; works only if the plugin is present
>      at doc-gen time (it is, per 4.2.A).
> Lean: **B** — the doc-gen path already works this way; extraction
> doesn't need to touch it.

### 5.8 PR sequence sketch

Conditional on 0.1.A (extraction sequences AFTER #68):

| PR | Title | Phase | Risk |
|---|---|---|---|
| #68.A | Package move v1 `*Rest` → `de.dlr.shepard.compat.v1.*` (in-tree) | #68 | low |
| #68.B (subset) | Inventory shared-IO classes; tag pure-v1 / share-stable / share-volatile | #68 | low |
| #68.B.1..n | Per-class IO disposition (move to compat / leave / duplicate) | #68 | medium |
| V1COMPAT.1 | Create empty `plugins/v1-compat/` module, manifest only | this | low |
| V1COMPAT.2 | Move 5 smallest endpoint groups to plugin (users, apiKeys, subscriptions, lab-journal, versionz) | this | medium |
| V1COMPAT.3 | Move references group (6 Rest classes) | this | high |
| V1COMPAT.4 | Move data containers (file/structured/timeseries) | this | high |
| V1COMPAT.5 | Move collections + dataObjects + semantic | this | high |
| V1COMPAT.6 | Move fixture corpus + `V5WireFidelityTest` per 5.1 | this | low |
| V1COMPAT.7 | `:V1CompatConfig` + admin REST + CLI parity | this | low |
| V1COMPAT.8 | Deprecation logging per 5.4 | this | low |
| V1COMPAT.9 | Tracker updates: `aidocs/34` + `aidocs/44` rows | this | low |

**Effort estimate.** 9 PRs in this design (after #68's 3–5 PRs). Per
PR: 1–3 agent-sessions. **Total: ~30 agent-sessions across 8–12
calendar weeks** for this doc alone; add 4–8 weeks for #68 prerequisite.

---

## 6. Risk register

| # | Risk | Probability | Blast radius | Mitigation |
|---|---|---|---|---|
| R1 | Shared-IO inventory (4.1) reveals more dual-use IOs than estimated, breaking the per-class plan | HIGH | Schedule slip (weeks) | Run the inventory spike (4.1.E) as a separate PR before sizing anything else; treat its output as authoritative |
| R2 | A v1 endpoint quietly relies on a core service that returns an IO type the plugin shouldn't own (e.g. `ExportService` returns `CollectionIO`) | MEDIUM | Per-endpoint refactor | The service-layer DTO-neutral fix is mandatory before that endpoint's extraction PR; size into PR estimates |
| R3 | Quarkus build-time CDI scan misses `@Path` resources in the plugin JAR under some Quarkus version | LOW | All v1 endpoints 404 | Reuse the `aas` / `minter-datacite` proven pattern; smoke-test in CI on every plugin module change |
| R4 | Deprecation log floods at MQTT-collector telemetry rate (home-showcase) | MEDIUM | Disk + log pipeline overload | 5.4.D (warn-once-per-pair); rate-limit per-process |
| R5 | Operator builds `-DnoV1Compat` and a forgotten v5 client breaks silently | LOW | Single-operator outage | 4.2.A makes the default plugin-on, so this only fires for operators who explicitly opted out; document loudly in `aidocs/34` |
| R6 | Wire-fidelity tests become flaky once they cross JAR boundary (classpath ordering) | MEDIUM | Test failures in CI | Run 5.1.C with the loud-skip guard; treat skipped fidelity = red CI |
| R7 | The L2 chain (shepardId Phase 2, task #123) lands inside this work and conflicts | HIGH | Merge conflicts | Sequence: complete the extraction first, THEN shepardId Phase 2 — or freeze v1 IO classes for the extraction window |

---

## 7. Sequencing + preconditions

Hard prerequisites (each must be DONE before V1COMPAT.1):

1. **SHACL substrate stable** — task #120 (semantic data lives in
   RDF, application data in Neo4j). Without this, IO classes still
   carry "data data" attributes that complicate the per-class
   inventory in 4.1.E.
2. **shepardId Phase 1 (#58) shipped** — the IO wire-rename is done;
   the additive `@JsonProperty("shepardId")` pattern on v2 subclasses
   is the proven template (`feedback_appid_to_shepardid.md` CRITICAL
   GOTCHA).
3. **v5 fixture corpus battle-tested in CI** — currently fixtures
   for 8 entity kinds exist (collections, dataobjects, file-, ts-,
   structured-containers, structured-data refs, users, permissions).
   Coverage gaps for `referenceIds`, semanticAnnotations, lab journal,
   versioning, search MUST be filled before extraction so the
   regression contract is complete.
4. **Baseline test failures cleared** — task #134 explicitly cited
   pre-existing failures; entering extraction with red CI hides
   regressions.
5. **`aidocs/platform/68` Phase A landed** — pure package rename to
   `de.dlr.shepard.compat.v1.*`. Without this, every extraction PR
   carries a package rename AND a JAR move; halve the cognitive
   load by separating them.

**Soft prerequisites (nice-to-have):**

- `aidocs/platform/68` Phase B done (services return DTO-neutral
  types; v1 REST classes are thin adapters). Without this, the
  plugin imports more core services than necessary.
- shepardId Phase 2 (#123) done — but per R7, this is BETTER scheduled
  after extraction; race conditions otherwise.

---

## 8. Persona review slate

The 8 standard personas (per CLAUDE.md). Recommended pre-implementation
reviewers in priority order:

| # | Persona | Why this design? | Specific lens |
|---|---|---|---|
| 1 | **API Scrutinizer (Minimalist)** | The plugin's product IS an API. Wire-shape decisions per 4.1 are core to API-shape concerns. | Inventory whether any v1 endpoint should NOT survive extraction (i.e. is the v1 freeze a freeze of cruft?). Adjudicate 2.1 (`temp/migrations`). |
| 2 | **Strategy Aligner & Executive Advisor** | The plugin is a multi-quarter investment. Question: does the deprecation timeline (4.3) align with funding-cycle realities? | Risk-weight 4.3.B vs. 4.3.C from a programme management angle. |
| 3 | **Reluctant Senior Researcher** | The operator at the receiving end of the v1 deprecation. The whole point of A defaults (4.2) is this persona. | Sanity-check 4.2 (default-on) and 4.3 (lifecycle); flag any signal that breaks pull-and-restart promise. |
| 4 | **Industrial Ecosystem Advocate** | Upstream relationship is a Shepard differentiator. The v1 plugin formalises "this fork stays compatible." | Assess whether the extraction strengthens or weakens the upstream-friendly positioning. |
| 5 | **Digital Native Researcher** | Will the deprecation warning land in their generated Python client? Will the OpenAPI doc still be useful? | Adjudicate 5.7 (OpenAPI generation) and the developer experience of opt-out via Maven profile. |

**Lower priority for this design (still useful, not blocking):**

- Data & Process Ontologist — IO classes carry semantic claims;
  worth a pass but the heavy lifting is the shape decision, not the
  semantics.
- Industrial Manufacturing & Quality Engineer — v1 endpoints are
  process-step ingress; risk if extraction breaks downstream QA scripts.
- Analytics & AI Opportunities Specialist — MCP surface is `/v2/`,
  not v1; minimal touchpoint.
- Research Data Manager (FAIR & Archival) — Publishing flows are
  `/v2/`; minimal touchpoint.
- Core Tech & UX Auditor — backend extraction, no UI; minimal touch.

---

## 9. Out of scope (deliberate)

- **Changing `/v2/` in any way.** This design is purely about the
  v1 surface. v2 endpoints, IO classes, and admin surface are
  untouched.
- **Deprecating `/v2/`.** v2 is the development surface for new work
  (per CLAUDE.md). Deprecating it is not on the table here.
- **Splitting Neo4j storage / Mongo / Timescale.** The data substrate
  is invariant under this design. Plugin imports services that read
  from the same DBs.
- **Renaming the wire path.** `/shepard/api/...` stays exactly that
  forever (the freeze rule). The Java package, the JAR, and the
  module move; the URL doesn't.
- **Deprecating the upstream Python / TS clients.** Both keep working
  unchanged by construction.
- **Changing v1 OpenAPI tags / groupings.** That's a downstream-client
  break; the freeze applies.
- **Building observability for community-signal deprecation
  (4.3.C).** Not in scope; if 4.3 = C, that telemetry is its own
  design doc.
- **`shepard-plugin-v2-compat` for some future v3 surface.** YAGNI;
  not designed here.

---

## 10. Tracker hooks (do not edit)

The eventual PR series must touch these files. Listed for the agent
who picks up implementation — DO NOT EDIT in this design PR.

- `aidocs/34-upstream-upgrade-path.md` — new row(s):
  - `V1COMPAT.A` — "Package rename `compat.v1.*`" (Phase A from #68)
  - `V1COMPAT.B` — "v1 surface extracted to `shepard-plugin-v1-compat`"
  - `V1COMPAT.C` — "v1 default-off at v7.0" (when that lands)
  - `V1COMPAT.D` — "v1 plugin module removed at v8.0"
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — new row "V1COMPAT
  plugin shipped"; potentially flip every v1 endpoint row to "shipped
  via plugin module".
- `aidocs/16-dispatcher-backlog.md` — task #134 status update; new
  sub-tasks per PR sketch §5.8.
- `CLAUDE.md` §"API-version policy" — append one paragraph noting
  the plugin as the runtime guarantor of the rule.
- `docs/reference/plugins.md` — new section for `shepard-plugin-v1-compat`.
- `docs/reference/v5-cross-instance-quirks.md` — cross-reference the
  plugin as the enforcement mechanism.
- `plugins/v1-compat/docs/{install,quickstart,reference}.md` — three
  new files (per CLAUDE.md §"plugins ship their own documentation").

---

## 11. Summary — clarification index

| ID | One-line question | Lean |
|---|---|---|
| 0.1 | Supersede / sequence / coexist with `aidocs/platform/68`? | **A — sequence** |
| 2.1 | `temp/migrations` endpoint — upstream or fork addition? | **B — verify; if fork, move to /v2/ first** |
| 4.1 | Shared-IO strategy across 37 IO classes? | **E — inventory-driven hybrid** |
| 4.2 | Default plugin state at extraction? | **A — enabled by default** |
| 4.3 | Deprecation timeline anchor? | **B — major-version-aligned** |
| 4.4 | Monolith or per-feature plugin? | **A — monolith with internal sub-packages** |
| 5.1 | v5 fixture corpus location? | **C — core, no-op gracefully when plugin absent** |
| 5.2 | Service-layer dependency direction? | **A — strict one-way** |
| 5.3 | Admin REST shape? | **A — single `:V1CompatConfig` config** |
| 5.4 | Deprecation log level? | **D — warn-once-per-pair + audit event** |
| 5.5 | Per-endpoint disable? | **YAGNI** |
| 5.6 | Test phase routing? | **A — Surefire + Failsafe split** |
| 5.7 | OpenAPI doc generation? | **B — core generates via classpath** |

**13 clarifications.** Implementation does not start until 4.1, 4.2,
4.3, 4.4 are answered. The rest can be answered as the corresponding
PR comes up.
