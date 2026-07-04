---
stage: feature-defined
last-stage-change: 2026-06-03
---

# V2CONV-A4 — generic `/v2/admin/config/{feature}` + ConfigRegistry — findings

Implementation findings for the admin-config consolidation slice of the
v2-surface-convergence (`aidocs/platform/191 §6`). Shipped 2026-06-03.

## What I found

- **Five bespoke admin-config REST classes existed**, all near-identical copy-paste
  of a `@RolesAllowed("instance-admin")` + GET + RFC-7396-PATCH + problem-JSON
  shell, differing only in the per-feature field set and validation:
  - `SemanticConfigRest` → `/v2/admin/semantic/config`
  - `SqlTimeseriesConfigRest` → `/v2/admin/sql-timeseries/config`
  - `InstanceRorConfigRest` → `/v2/admin/instance/ror`
  - `JupyterConfigRest` → `/v2/admin/jupyter/config` (a `@Deprecated` shim)
  - `JupyterConfigPluginRest` → `/v2/admin/plugins/jupyter/config` (the J1e-PR-07
    "canonical" replacement for the shim)
- The **Jupyter feature already carried the cost this slice removes**: J1e-PR-07
  had split one config endpoint into a canonical path + a deprecated shim, with a
  WARN-on-every-call and a tracked-but-unfinished relocation. V2CONV-A4 retires
  **both** in one move — the generic surface is the relocation's natural endpoint.
- The **tri-state merge-patch problem (absent / null / value) was solved per-feature**
  with `@JsonSetter(nulls = SET)` + `*Touched` flags in bespoke patch-IO classes.
  The generic resource cannot know a feature's field set ahead of time, so I pass
  the parsed `JsonNode` to each descriptor — preserving the distinction without a
  typed DTO. Web research (RFC 7396, `datatracker.ietf.org/doc/html/rfc7396`)
  confirmed the null-vs-absent semantics; the Quarkus issue trail
  (quarkus#33186, #37980) confirmed the built-in `application/merge-patch+json`
  filter has Jackson-interop bugs — so keeping the explicit per-field mapping is
  the correct, low-risk choice, not a missed shortcut.
- The **A3b `FeatureToggleRegistry`** (`/v2/admin/features`) is the exact registry
  shape to mirror: `@ApplicationScoped`, indexes entries on `@Observes StartupEvent`,
  fail-soft `Optional` resolve, generic resource delegating by path segment. I
  reused that shape; the only delta is descriptor discovery via CDI `Instance<>`
  rather than hand-registration, so a new feature needs zero registry edits.

## Opportunities

- **Adding a runtime-configurable feature is now zero-REST-boilerplate.** Drop a
  `ConfigDescriptor` `@ApplicationScoped` bean next to the existing
  `*ConfigService`; the registry auto-discovers it and `/v2/admin/config/{feature}`
  serves it. This is the structural payoff §6 promised.
- **`GET /v2/admin/config` is a new discovery affordance** the bespoke classes
  never had — an operator (or the admin UI) can enumerate configurable features
  without hard-coding the keys. A future admin "Config" hub-tile could render one
  generic pane per descriptor from this listing + a per-descriptor JSON-schema.
- **JSON-schema-per-descriptor is the obvious next increment.** `ConfigDescriptor`
  could grow a `jsonSchema()` for OpenAPI + client-side validation + a fully
  generic admin form (the design doc names `jsonSchema()`/`patchableFields()`).
  I kept the SPI minimal (`currentShape`/`applyMergePatch`) to avoid speculative
  surface; the schema hook is a clean additive follow-up.

## Ideas

- A `ConfigDescriptor.cliVerbs()` projection could regenerate the
  `shepard-admin <feature> {status,set-…}` CLI parity from the same descriptor,
  collapsing the per-feature CLI command classes the way this collapsed the REST
  classes — the same A3b-style consolidation one layer down.
- A single `ProvenanceCaptureFilter` already stamps the PATCH `:Activity` with
  `targetKind` from the resource path; because the path is now uniform
  (`/v2/admin/config/{feature}`), a future enrichment could read `{feature}` into
  the Activity so "who changed the jupyter config when" stays one Cypher query
  even though the REST class is shared.

## Real-world impact

- **For an operator:** the admin-config URLs moved (BREAKING, documented in
  `aidocs/34`), but request/response bodies are byte-identical. `GET /v2/admin/config`
  lists the four keys; `curl … PATCH /v2/admin/config/ror -d '{"rorId":"04cvxnb49"}'`
  works exactly as the old ROR endpoint did. Scripts/monitoring/IaC need a one-line
  URL update; the admin UI panes were repointed in the same release.
- **For a contributor:** the next `:*Config` feature is a descriptor bean, not a
  REST class — less to write, less to get wrong, and the role-gate / problem-JSON /
  provenance contract is centralised so it cannot drift between features.
- **For the convergence arc:** this removes 5 endpoint families from the 232-endpoint
  `/v2` surface and replaces them with 1 generic family — a concrete down-payment on
  the "minimalist core" thesis, and it retires the J1e-PR-07 dual-path debt as a
  side effect.

## Gaps & blockers

- **Integration tests (`*IT`) could not run in the isolated worktree** — all 48 IT
  failures are `BaseTestCaseIT.getNewUserWithApiKey` Keycloak-auth failures
  ("client provided incorrect authentication details too many times"), i.e. the
  worktree lacks the live docker-compose Keycloak/Neo4j stack. They are unrelated
  to this change (none reference config classes); the 4589 unit tests + JaCoCo +
  SpotBugs all pass. A live-stack IT asserting `GET /v2/admin/config/semantic`
  ≡ old `/v2/admin/semantic/config` + `:Activity` capture is the right regression
  guard and should run in CI where the stack is available.
- **The frontend `npm run lint` gate has a pre-existing baseline failure** (56
  errors in untouched test files — `useLineageGraph.test.ts`, `useFetchChannelPreview.test.ts`,
  etc., from prior commits like UI16). My 10 changed frontend files lint clean
  (verified with a scoped `npx eslint`). This lint debt predates and is orthogonal
  to V2CONV-A4.
- **The `:*Config` services have mixed CDI scopes** — `OntologyConfigService` is
  `@RequestScoped` while the others are `@ApplicationScoped`. The descriptors are
  `@ApplicationScoped` and inject the services directly; CDI's client proxy handles
  the request-scoped one correctly at call time. Worth noting for the next descriptor
  author: inject the service, never cache its instance.

## What surprised me

- **The Jupyter feature had already paid for this twice.** It shipped at one path
  (J1e), then split into canonical-path + deprecated-shim (J1e-PR-07) with a
  per-call WARN and an open relocation obligation. The "consolidate the REST shell"
  framing of A4 turns out to be the cleanest possible *resolution* of that earlier
  split — the generic surface is where the relocation was always heading. The
  lesson: per-feature endpoint proliferation accretes its own follow-on debt
  (shims, dual-paths, deprecation windows), and the registry pattern dissolves all
  of it at once.
- **The bespoke classes were ~150 lines each of almost-identical ceremony.** The
  descriptors are ~70–110 lines and carry *only* the field mapping + validation —
  every line of role-gate, problem-JSON envelope, and provenance handoff that was
  duplicated 5× now lives once in `AdminConfigRest`. The duplication wasn't
  accidental; it was the only shape available before a registry existed.
