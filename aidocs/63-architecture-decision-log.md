# 63 — Architecture Decision Log

**Purpose.** Append-only record of architectural / design decisions
made during work on `noheton/shepard`. Each entry captures what was
decided, why, the alternatives considered, and the reversibility
posture. The intended reader is future-me / a future contributor
trying to understand why the code looks the way it does without
re-deriving the trade-offs from scratch.

**Conventions.**
- Entries are append-only. If a decision is overturned, write a new
  entry citing the prior one rather than editing it.
- Each entry has a stable ID (`ADR-NNNN`) so commit messages,
  comments, and other aidocs can cite it.
- **Status** is one of: `proposed`, `accepted`, `superseded by …`,
  `reverted`. A decision becomes `accepted` once it ships in code.
- **Reversibility** is one of: `easy` (one-PR revert), `moderate`
  (some downstream code touched), `hard` (migration / wire-shape
  implication), `one-way` (essentially permanent without an
  operator-visible migration).
- Citation form: `ADR-NNNN` in the body of any commit / aidoc that
  applies the decision; the entry then back-fills the citation in
  its "applied in" line.

---

## Index

| ID | Status | Date | Decision | Reversibility |
|---|---|---|---|---|
| ADR-0001 | accepted | 2026-05-12 | Keep `*Reference` suffix on FileBundleReference / VideoStreamReference (consistency over brevity) | easy |
| ADR-0002 | accepted | 2026-05-12 | Two-type FileReference design: singleton `FileReference` + multi `FileBundleReference` | moderate |
| ADR-0003 | accepted | 2026-05-12 | NovaCrate integrates as an external "Open in NovaCrate" link, not as a library | easy |
| ADR-0004 | accepted | 2026-05-12 | PROV-O over OpenLineage for the audit-trail / activity-capture stack | hard |
| ADR-0005 | accepted | 2026-05-12 | FR1a Java rename via `@Labels` two-label trick + `getType()` pin for upstream wire compat | moderate |
| ADR-0006 | accepted | 2026-05-12 | FR1b singleton uses distinct `:SingletonFileReference` label, not a property discriminator on `:FileReference` | moderate |
| ADR-0007 | accepted | 2026-05-12 | V23 split-singletons migration is opt-in (`shepard.migration.split-singletons.enabled=false`) | easy |
| ADR-0008 | accepted | 2026-05-12 | AI1c quality-scoring heuristic = mean of completeness + coverage + stability | easy |
| ADR-0009 | accepted | 2026-05-12 | AI1c default-off; opt-in via `shepard.timeseries.quality-scoring.enabled=true` | easy |
| ADR-0010 | accepted | 2026-05-12 | P4c URL convention: `/shepard/doc/openapi/{v1,v2}.json` (mirrors combined-doc path), not `/shepard/api/doc/openapi/...` | easy |
| ADR-0011 | accepted | 2026-05-12 | V2NamespaceTest exempts `de.dlr.shepard.common.openapi` from the `/shepard/api/` prefix rule | easy |
| ADR-0012 | accepted | 2026-05-12 | A5a (HDF5/HSDS Phase 1) uses HTTP Basic auth; Keycloak token relay deferred to A5e | moderate |
| ADR-0013 | accepted | 2026-05-12 | A5a HSDS sidecar ships as opt-in compose profile `hdf` (off by default) | easy |
| ADR-0014 | accepted | 2026-05-12 | N1a (n10s plugin) fails soft when the plugin is absent — bootstrap logs WARN + skips | easy |
| ADR-0015 | accepted | 2026-05-12 | L1 Phase 1 CLI ships as standalone Maven module, no root aggregator pom | moderate |
| ADR-0016 | accepted | 2026-05-12 | DX2 ShepardTestFixtures ships one canonical Reference builder (`BasicReference`), not one per kind | easy |
| ADR-0017 | accepted | 2026-05-12 | FR1b UI converts singleton → bundle ONLY (never bundle → singleton) on drag-count change | easy |
| ADR-0018 | accepted | 2026-05-12 | Drop FR1b first attempt: prior agent overwrote frozen upstream `FileReferenceIO` / `FileReferenceRest` | one-way (abandoned branch) |
| ADR-0019 | accepted | 2026-05-12 | Pre-seed common ontologies default-on for casual users on day one | easy |
| ADR-0020 | accepted | 2026-05-12 | shepard is source of truth for HDF container ACLs (HSDS-side mutations clobbered) | moderate |
| ADR-0021 | accepted | 2026-05-12 | GitLab-only adapter in G1b; GitHub + Gitea ship in G1d via the GitAdapter interface seam | easy |

---

## ADR-0001 — Keep `*Reference` suffix on renamed primitives

**Status.** accepted. **Date.** 2026-05-12.
**Applied in.** `aidocs/53 §1.2`; FR1a (PR #1076).

### Context

The original `aidocs/53 §1.2` proposed renaming `FileReference` to
`FileBundle` (dropping the `Reference` suffix) on the grounds that
"Reference" is a structural detail, not a user-facing concept. The
same logic was extended to `VideoStream` (vs `VideoStreamReference`)
in §2.2. The argument was that researchers think "I'm uploading a
bundle of files," not "I'm creating a FileReference."

### Decision

Keep the `Reference` suffix. The renamed primitives are
`FileBundleReference` (multi) and (eventually) `VideoStreamReference`.

### Rationale

- The four current Reference primitives (`TimeseriesReference`,
  `StructuredDataReference`, `SpatialReference`, `BasicReference`) are
  a family — the shared suffix is what makes the family read as one
  in code review, in OpenAPI, and in the `/shepard/api/` routing
  tree.
- Two extra syllables ("Reference") is the cost; the benefit is a
  stable mental model and uniform cross-cutting code (loops over "all
  Reference kinds" stay readable).
- The `FileGroup` sub-node legitimately drops the suffix because it
  is **not** a Reference primitive — it's a sub-node owned by one.
  The suffix is a family marker, not a generic ornament.

### Alternatives considered

- (ii) `FileBundle` (drop suffix) — rejected; breaks the family.
- (iii) `FileCollectionReference` — rejected; "Collection" clash
  with the top-level primitive.
- (iv) `FileSetReference` — rejected; "Set" implies uniqueness,
  which the model doesn't enforce.

### Reversibility

Easy. The decision is fully internal + `/v2/`-surface only; upstream
`/shepard/api/...fileReferences/...` keeps the `FileReference` name
forever per CLAUDE.md §API-version-policy.

---

## ADR-0002 — Two-type FileReference design

**Status.** accepted. **Date.** 2026-05-12.
**Applied in.** `aidocs/53 §1.8`; FR1a (PR #1076) ships
`FileBundleReference`; FR1b (PR #1080) re-introduces singleton
`FileReference`.

### Context

The original `aidocs/53 §1.2` collapsed two distinct casual
workflows ("drop a single PDF" and "1 000-frame capture run") into a
single `FileBundleReference` primitive. Single-file users would
upload "a bundle of one file," forcing a synthetic
`default FileGroup` wrapper.

### Decision

Land two parallel primitives:
- **`FileReference`** — exactly one `ShepardFile`, no
  `FileContainer`, bytes in a shared `_shepard_files` GridFS
  namespace.
- **`FileBundleReference`** — ≥ 1 `ShepardFile`s, organised into
  ≥ 1 `FileGroup` sub-nodes (the §1.3-§1.7 design).

UI picks the type by drag-count: 1 file → `FileReference`, ≥2 →
`FileBundleReference`.

### Rationale

- Single-PDF users don't think in bundle terms; forcing them through
  the bundle path is conceptual overhead.
- Singleton path skips `FileContainer` entirely — one shared Mongo
  namespace, no per-reference collection.
- Annotations / lab-journal pins to "this PDF" are clean — no
  synthetic "default group" indirection.
- Wire shape stays sharp: `GET /v2/files/{appId}` returns one file's
  metadata; `GET /v2/bundles/{appId}` returns the bag with groups.

### Alternatives considered

- **`FileBundleReference` only** (the §1.2-§1.7 original) — every
  payload is a bag; singleton case pays bag overhead.
- **`FileReference` only** (no bundles) — capture-run case loses
  "these N files belong together" grouping.

### Reversibility

Moderate. Two-phase migration:
- V21 (FR1a) — additive, always-on; renames everything to bundles.
- V23 (FR1b) — opt-in carve-out via
  `shepard.migration.split-singletons.enabled` (see ADR-0007).

An operator who never runs V23 still gets the singleton-as-bundle
shape, which is correct but non-optimal. Reverting the two-type
design would require collapsing back through V23_R.

---

## ADR-0003 — NovaCrate integration via external link only

**Status.** accepted. **Date.** 2026-05-12.
**Applied in.** `aidocs/62-novacrate-evaluation.md`; R4 row in
`aidocs/16`.

### Context

R4 requested an evaluation of whether NovaCrate
(`novacrate.datamanager.kit.edu`) should be integrated as a library
for in-app RO-Crate metadata editing.

### Decision

Surface an "Open in NovaCrate" deep link on the export-result panel
+ a pointer in `docs/help/export-rocrate.md`. **Do not** bundle, do
not iframe-embed, do not add a Maven dependency.

### Rationale

- NovaCrate is a web app, not a library. No Maven artifact exists;
  no integration surface for the backend.
- Same KIT Data Manager group as our existing `ro-crate-java`
  producer library — natural ecosystem fit, but on the consumer
  side.
- The user-value is "I exported a crate; now I want to edit it" —
  the external editor link covers that without owning a second app's
  release lifecycle.

### Alternatives considered

- **Option B: iframe-embed** — rejected; UX friction + loses
  NovaCrate's "data stays on device" property.
- **Option C: NovaCrate-hosted upload endpoint** — rejected;
  endpoint doesn't exist today, requires upstream coordination.
- **Option D: round-trip via crate import** — rejected; that's a
  separate feature (`R10-rocrate-import`, not yet designed), not a
  NovaCrate integration.

### Reversibility

Easy. The whole touch is one frontend link + one docs paragraph.

---

## ADR-0004 — PROV-O over OpenLineage for the audit-trail stack

**Status.** accepted. **Date.** 2026-05-12 (effectively decided
earlier in `aidocs/55`).
**Applied in.** PROV1 series (PROV1a-g, shipped 2026-05-12).
**Cited.** Comment on issue #695 explaining the spec choice.

### Context

Issue #695 ("Audit trail") suggested OpenLineage as the
audit-trail / lineage specification. The PROV1 design
(`aidocs/55-provenance-design.md`) picked W3C PROV-O instead.

### Decision

Use W3C **PROV-O** for the audit-trail ontology + `:Activity` Neo4j
model + PROV-N JSON export. Maintain OpenLineage compatibility as
out-of-scope (no current demand surfaced).

### Rationale

- PROV-O has stronger ontology support — composes with the planned
  semantic-annotations work via n10s (`aidocs/48` / N1a).
- PROV-O's query shape is closer to "render a reverse history per
  Collection" than OpenLineage's run-graph model.
- PROV-N JSON export means PROV-O lineage can still be consumed by
  any PROV-aware tool downstream.
- OpenLineage targets pipeline-orchestration tools (Airflow / dbt /
  Spark); shepard's audit-trail is graph-shaped per-entity, not
  job-shaped.

### Alternatives considered

- **OpenLineage** — rejected as primary; possible future bridge if
  pipeline-integration demand emerges.
- **Custom shape, no public ontology** — rejected; loses
  interoperability.

### Reversibility

Hard. The `:Activity` entity shape is PROV-O-aligned; switching
would require a data migration + wire-shape change on
`/v2/provenance/*`. The PROV-N export endpoint stays valuable
regardless.

---

## ADR-0005 — FR1a Java rename via @Labels two-label trick

**Status.** accepted. **Date.** 2026-05-12.
**Applied in.** FR1a (PR #1076), commit `c53d055` (rename).

### Context

FR1a renamed the Java entity `FileReference.java` to
`FileBundleReference.java`. The Neo4j label `:FileReference` is
bound to the upstream-API wire (`BasicReferenceIO.type =
"FileReference"`). New nodes need both `:FileReference` (legacy
compat) and `:FileBundleReference` (new primary) labels.

### Decision

Use Neo4j-OGM `@Labels` to write **both** labels on every save:
- `@NodeEntity(label = "FileReference")` keeps the legacy label
  active.
- A `@Labels` field on the entity adds `FileBundleReference`
  dynamically.
- `getType()` overridden to return `"FileReference"` so the
  `BasicReferenceIO.type` JSON field stays byte-stable.

### Rationale

- Single-source-of-truth for the entity — no parent-class
  inheritance chain to maintain.
- New nodes are correctly dual-labelled on first save (no need to
  re-run V21 to back-fill the second label).
- V21 migration becomes additive-only (no behaviour-change for
  existing nodes beyond the new label being added).

### Alternatives considered

- **Parent-class inheritance** (`FileBundleReference extends
  FileReference` for label inheritance) — rejected; introduces an
  unused base class purely for label-management.
- **DAO-side post-save hook** to add the second label — rejected;
  bypasses OGM's natural seam and requires reapplication if the
  hook is ever lost.
- **Drop the legacy label entirely, use `:FileBundleReference` only**
  — rejected; breaks the upstream-API `getType()` wire promise.

### Reversibility

Moderate. Reverting requires a data migration to either drop the
new label (`V21_R__*.cypher`) or split the legacy label off.

---

## ADR-0006 — FR1b singleton uses distinct `:SingletonFileReference` label

**Status.** accepted. **Date.** 2026-05-12.
**Applied in.** FR1b (PR #1080), entity at
`backend/src/main/java/de/dlr/shepard/context/references/file/entities/FileReference.java`.

### Context

FR1b re-introduces a singleton `FileReference` primitive alongside
the multi-file `FileBundleReference` (per ADR-0002). Both kinds need
to coexist in Neo4j without DAO query confusion — FR1a's bundle DAO
already queries `:FileReference` (the compat label).

### Decision

Tag singleton FileReferences with a **distinct** label
`:SingletonFileReference`. The FR1a bundle DAO queries
`:FileBundleReference`; the new FR1b singleton DAO queries
`:SingletonFileReference`. The `:FileReference` compat label
remains on bundles only (for upstream-API byte-compat).

### Rationale

- Disjoint row sets: bundle DAO and singleton DAO can't cross-read
  each other's rows by accident, even if a Cypher query forgets a
  filter.
- No DAO-level Cypher predicate gymnastics (every query would have
  needed `WHERE n.singleton = true/false` otherwise).
- Migration semantics stay clean: V23 carve-out atomically swaps
  labels from bundle to singleton, no boolean discriminator to flip
  in a separate pass.

### Alternatives considered

- **Property discriminator** (`@Property("singleton") boolean
  singleton`) — rejected; requires DAO Cypher predicates everywhere
  and creates a data-state-vs-label-state divergence risk.
- **Single `:FileReference` label for both** — rejected; FR1a's
  compatibility label is already populated by every bundle.

### Reversibility

Moderate. Reverting requires V23 rollback + a label sweep.

---

## ADR-0007 — V23 split-singletons migration is opt-in

**Status.** accepted. **Date.** 2026-05-12.
**Applied in.** FR1b (PR #1080); config key
`shepard.migration.split-singletons.enabled=false` default.

### Context

V23 carves singleton-shaped `FileBundleReference`s (one file, one
default group) out into the new singleton `FileReference` shape per
ADR-0002. The migration moves Mongo metadata between collections —
a non-trivial data operation.

### Decision

V23 is **opt-in** via `shepard.migration.split-singletons.enabled`,
default `false`. Operators flip the toggle when ready, take backups,
and restart. Greenfield installs may flip it at install time (no-op
until bundles exist).

### Rationale

- Moving Mongo bytes between collections wants operator scheduling
  (potentially overnight on a large dataset).
- A fresh install always uses the new shape from day one — no
  migration needed.
- Operators who never flip the toggle ship with everything-as-bundles
  and lose nothing — the singleton-as-bundle shape is correct, just
  non-optimal.

### Alternatives considered

- **Always-on V23 (default `true`)** — rejected; surprises
  operators with byte-mutation on first restart.
- **One-time CLI command** instead of a migration toggle — rejected;
  inconsistent with the rest of the migrations infrastructure.

### Reversibility

Easy via `V23_R__*.cypher` rollback (refuses if any user-created
singleton exists OR any V23-minted singleton has been patched —
timestamp guard).

---

## ADR-0008 — AI1c quality-scoring heuristic shape

**Status.** accepted. **Date.** 2026-05-12.
**Applied in.** AI1c (PR #1079), `TimeseriesQualityScorer.java`.

### Context

AI1c added a `qualityScore` (`[0.0, 1.0]`) attribute on every
`TimeseriesReference`. The scoring is **pure heuristic** (no LLM, no
ML model), per `aidocs/43` AI1c row.

### Decision

`qualityScore = mean(completeness, coverage, stability)` where:
- **completeness** = `1.0 − missing_value_ratio` over the last
  1 000 sample window.
- **coverage** = `observed_points / expected_points` clamped to
  `[0, 1]`, where `expected = (last_ts − first_ts) / median_dt`.
- **stability** = `1.0 − clamp(stddev / mean, 0, 1)` (skipped if
  `mean == 0`).

Insufficient-data threshold: fewer than 10 points → `qualityScore =
null` (intentionally distinguishable from a low score).

### Rationale

- Each signal is computable from a single pass over the sample
  window — O(N) where N ≤ 1 000.
- Three orthogonal axes capture three distinct failure modes
  (gappy / sparse / noisy).
- Null-distinguishable insufficient-data prevents misreading "no
  data" as "low quality."

### Alternatives considered

- **Single coverage metric only** — rejected; misses the "very
  noisy but dense" signal that's structurally bad data.
- **LLM-judged quality** — rejected; AI1c is explicitly LLM-free
  (AI1a/AI1d territory).
- **Configurable weights per signal** — rejected for v1; even
  weights ship first, configurability added if a user surfaces a
  reason.

### Reversibility

Easy. The heuristic is a single class
(`TimeseriesQualityScorer`); replacing it leaves the wire shape
intact (clients see the same `qualityScore` field shape).

---

## ADR-0009 — AI1c default-off

**Status.** accepted. **Date.** 2026-05-12.
**Applied in.** AI1c (PR #1079); config keys default values.

### Context

AI1c's `TimeseriesQualityScoringJob` runs on `@Scheduled` and reads
every non-deleted `:TimeseriesReference`. The DB-load cost is
non-zero on instances with many timeseries.

### Decision

The job is **default-off**: `shepard.timeseries.quality-scoring.enabled=false`.
Operators opt in explicitly. When disabled, the scheduled tick logs
at debug and does no DB I/O.

### Rationale

- Casual installs don't need quality scoring on day one.
- Heavy installs (many timeseries) decide whether to absorb the
  scoring cost and pick a sensible interval / batch size.
- Default-off matches the pattern of every other optional
  background job (PROV1, A1f recovery scheduler, etc.).

### Alternatives considered

- **Default-on with conservative interval** (e.g. `PT24H`) —
  rejected; surprise CPU/DB load on existing installs.
- **Auto-detect "enough timeseries" and self-enable** — rejected;
  hidden auto-behaviour is harder to debug than explicit toggles.

### Reversibility

Easy. Config-flag change; no schema implication.

---

## ADR-0010 — P4c URL convention preserves combined-doc path style

**Status.** accepted. **Date.** 2026-05-12.
**Applied in.** P4c (PR #1077); paths `/shepard/doc/openapi/v1.json`
and `/shepard/doc/openapi/v2.json`.

### Context

P4c split the combined `/openapi.json` (served by smallrye-openapi
at `/shepard/doc/openapi.json` historically per
`PublicEndpointRegistry` comments) into two per-shelf documents.
The first agent picked `/shepard/doc/openapi/{v1,v2}.json` (mirror
the existing convention); a follow-up auto-fence-fix proposed
`/shepard/api/doc/openapi/...` to satisfy `V2NamespaceTest` rule 3.

### Decision

Keep the URLs at `/shepard/doc/openapi/{v1,v2}.json`. **Don't**
shoehorn them under `/shepard/api/`.

### Rationale

- The per-shelf docs are **documentation routes**, not REST
  resources — they sit beside the `/shepard/api/...` and `/v2/...`
  shelves, not inside either.
- Putting docs under `/shepard/api/` would imply they're part of
  the frozen upstream surface, which they aren't (they're
  meta-information about that surface, and they emit new content
  when shepard adds endpoints).
- Mirrors the existing combined-doc convention — operators don't
  learn two different URL patterns for two different OpenAPI
  documents.

### Alternatives considered

- **`/shepard/api/doc/openapi/{v1,v2}.json`** — rejected; pollutes
  the frozen upstream surface with docs URLs.
- **Move `OpenApiPerShelfRest` to `de.dlr.shepard.v2.openapi`** —
  rejected; the v2 fence then requires `/v2/...` prefix, which is
  ironic for the doc describing the v1 shelf.

### Reversibility

Easy. Three files (REST class @Path + two test paths + registry
allowlist) plus docs cross-references.

### Coupled with

ADR-0011 — the V2NamespaceTest exception enables this decision.

---

## ADR-0011 — V2NamespaceTest exempts the OpenAPI emission package

**Status.** accepted. **Date.** 2026-05-12.
**Applied in.** P4c (PR #1077); `V2NamespaceTest.java` adds
`.resideOutsideOfPackage("de.dlr.shepard.common.openapi")` to rule 3.

### Context

`V2NamespaceTest` rule 3 (post-P4) requires that every non-v2 JAX-RS
class use `/shepard/api/` prefix. P4c's `OpenApiPerShelfRest` mounts
at `/shepard/doc/openapi/...` per ADR-0010.

### Decision

Add a single-package exemption to rule 3: classes in
`de.dlr.shepard.common.openapi` are exempted from the `/shepard/api/`
prefix requirement.

### Rationale

- The fence's intent is "catch accidental `@Path("/foo")` mounts at
  the application root." The OpenAPI emission class is **deliberate**,
  documented, and reviewed — the exemption captures the distinction
  without weakening the rule for actual REST resources.
- One-package scope keeps the fence strict for everything else; any
  future addition to `de.dlr.shepard.common.openapi` should be
  reviewed against this same exemption rationale.

### Alternatives considered

- **Remove rule 3 entirely** — rejected; the rule has caught real
  accidents elsewhere.
- **Add an annotation `@PlatformPath` that exempts** — rejected;
  over-engineering for one class.
- **Inline class allowlist in the rule** — rejected; package-level
  is more durable than class-level.

### Reversibility

Easy. Single test predicate change.

---

## ADR-0012 — A5a Phase 1 uses HTTP Basic auth (Keycloak relay deferred)

**Status.** accepted. **Date.** 2026-05-12.
**Applied in.** A5a (PR pending); `HsdsClient.java`,
config key `shepard.hdf.hsds.{username,password}`.

### Context

`aidocs/35` recommended HSDS sidecar + shared-Keycloak token relay
as the long-term auth path. Phase 1 (A5a) needs an auth mechanism
that ships now without a Keycloak realm.

### Decision

Use HTTP Basic auth in Phase 1. Admin user + password set in
shepard's config (`shepard.hdf.hsds.username` / `.password`).
Per-user identity relay (A5e) deferred until A5b/c/d bed in.

### Rationale

- HSDS supports HTTP Basic natively; no custom adapter needed in
  Phase 1.
- Phase 1 is admin-managed by design — A5b adds the permission
  bridge from shepard's per-user ACL to HSDS ACLs in a later slice.
- Shipping Phase 1 with Keycloak as a hard dependency would gate
  HDF5 on a shared-realm operator decision that not every install
  has made.

### Alternatives considered

- **Ship A5a + A5e together** — rejected; bigger slice, harder to
  isolate failure modes.
- **No auth in Phase 1** — rejected; HSDS would be open to the
  compose network.

### Reversibility

Moderate. Phase 1 → A5e migration adds a new auth path while
keeping HTTP Basic as a fallback during a deprecation window.

---

## ADR-0013 — A5a HSDS compose profile off by default

**Status.** accepted. **Date.** 2026-05-12.
**Applied in.** A5a (PR pending); `infrastructure/docker-compose.yml`
profile `hdf`, off by default.

### Context

The HSDS sidecar adds a new container service to the stack. Casual
installs don't necessarily need HDF5 support.

### Decision

Ship HSDS as a compose profile `hdf` that's **off by default**.
Operators who want HDF5 run `docker compose --profile hdf up -d`.

### Rationale

- Mirrors the established pattern (`spatial`, `files-s3`,
  `monitoring`, `video-ingest` per `aidocs/53`).
- Keeps the default-stack footprint minimal — RAM / disk / open
  ports.
- `shepard.hdf.enabled=false` is the matching backend toggle; if
  the profile is off, the backend's HDF endpoints return 404
  (feature off, mirror `SpatialDataFeatureToggle`).

### Alternatives considered

- **Default-on profile** — rejected; surprise resource consumption.
- **Separate compose file** — rejected; profiles are the
  established convention.

### Reversibility

Easy.

---

## ADR-0014 — N1a fails soft when n10s plugin is absent

**Status.** accepted. **Date.** 2026-05-12.
**Applied in.** N1a (PR #1082); `N10sBootstrapHook`.

### Context

`aidocs/48` recommended bundling the neosemantics (n10s) plugin in
the Neo4j compose service. Operators on existing compose files
won't have n10s until they refresh.

### Decision

The bootstrap hook checks `CALL dbms.procedures()` for `n10s.*`. If
absent → log one `WARN` and skip n10s init. `SemanticRepositoryType.INTERNAL`
repos then report `healthCheck() == false`; the other connector
types (`SPARQL`, `JSKOS`, `SKOSMOS`) keep working.

### Rationale

- Backwards-compatible with operators who haven't refreshed their
  compose file — shepard starts cleanly, just without the INTERNAL
  repo type available.
- The warning makes the cause discoverable in logs.
- Idempotent — re-init when n10s appears (operator refreshes
  compose, restarts shepard) just works.

### Alternatives considered

- **Fail-fast** if n10s missing — rejected; breaks compose
  upgrades.
- **Auto-install n10s** at startup — rejected; outside shepard's
  responsibility scope; the compose file is the right place.

### Reversibility

Easy.

---

## ADR-0015 — L1 Phase 1 CLI as standalone Maven module

**Status.** accepted. **Date.** 2026-05-12.
**Applied in.** L1 Phase 1 (PR #1078); new `cli/` module.

### Context

The original L1 Phase 1 prompt asked for the new `cli/` module to
be added to a root `pom.xml`'s `<modules>` block. The L1 agent
discovered no such root pom exists (`backend/` is the only Java
tree; `backend-client/` is TypeScript).

### Decision

Ship `cli/` as a standalone Maven module — no aggregator pom. Build
locally with `cd cli && mvn package`.

### Rationale

- Matches the existing repo shape (each top-level Java/TS tree is
  independent).
- Avoids inventing a root aggregator pom just for the CLI.
- Future modules can opt into a root aggregator if/when one is
  worth introducing — but it's not blocking for the CLI's release.

### Alternatives considered

- **Add a root aggregator pom** — rejected; introduces a new
  build-tree shape for one module.
- **Build the CLI inside `backend/`** — rejected; CLI is a
  consumer of the backend's API, not a sub-build of it.

### Reversibility

Moderate. Introducing a root aggregator later is a small refactor.

---

## ADR-0016 — DX2 ships one canonical Reference builder

**Status.** accepted. **Date.** 2026-05-12.
**Applied in.** DX2 (PR #1081); `ShepardTestFixtures.java`.

### Context

DX2 (`ShepardTestFixtures`) added typed builders for the
most-stubbed primitives. The Reference family has many kinds
(`BasicReference`, `FileBundleReference`, `TimeseriesReference`,
etc.).

### Decision

Ship one canonical Reference builder (`BasicReference`) in v1. The
remaining kinds can be added incrementally as tests need them.

### Rationale

- DX2's value is the **pattern**, not exhaustive coverage. One
  example demonstrates the shape; follow-up DX items can widen.
- Each Reference kind has slightly different constructor / property
  needs; v1 doesn't need to model all of them speculatively.

### Alternatives considered

- **One builder per Reference kind** — rejected; speculative work
  for kinds no test currently needs.
- **Generic `aReference(kind)` factory** — rejected; loses
  type-safety, ergonomics worse than copy-paste.

### Reversibility

Easy. Add more builders later as needed.

---

## ADR-0017 — FR1b UI: singleton → bundle conversion only

**Status.** accepted. **Date.** 2026-05-12.
**Applied in.** `aidocs/53 §1.8.7`. FR1b backend lands the
single-direction shape; FR1b-ui ships the UI.

### Context

The drag-count UI dispatch (ADR-0002) creates a `FileReference` on
1-file drop and a `FileBundleReference` on ≥2. A user who starts
with one file and adds a second mid-upload is a natural case.

### Decision

Conversion direction is **singleton → bundle only**. A user
realising mid-upload they want bundle structure (e.g. they intend
to attach a second file) converts forward via a "wrap as bundle"
toggle. Reverse conversion (bundle → singleton) is not supported —
a user wanting to "un-bundle a singleton" deletes + re-uploads.

### Rationale

- Reverse conversion is ambiguous: which group does the
  re-orphaned file belong to? what about the bundle's name + group
  attributes?
- Single-direction conversion preserves the bundle's metadata
  history (which would otherwise need to be discarded on revert).
- Delete + re-upload is rare enough that the UX cost is acceptable.

### Alternatives considered

- **Bidirectional conversion** — rejected; ambiguity around bundle
  metadata.
- **No conversion at all** — rejected; the "expected one file but
  got more" case is real and friendly handling is important.

### Reversibility

Easy. UI-only behaviour; not encoded in the wire shape.

---

## ADR-0018 — Drop FR1b first-attempt branch

**Status.** accepted (operational decision). **Date.** 2026-05-12.

### Context

The first FR1b agent dispatch overwrote the frozen upstream
`FileReferenceIO` and `FileReferenceRest` classes — a CLAUDE.md
§API-version-policy violation.

### Decision

Abandon the first FR1b branch entirely. Re-dispatch with a fresh
worktree, explicit "do not touch the frozen upstream classes"
guardrails, and the corrected design (per ADR-0006).

### Rationale

- Salvaging the broken branch by surgically restoring the
  overwritten files leaves a low-quality history (mixed
  intent-vs-fix commits).
- The fresh dispatch costs one extra agent run but delivers a
  cleaner PR; the second attempt landed FR1b #1080 with 100%
  upstream-freeze compliance verified post-hoc.

### Alternatives considered

- **Surgical fix-in-place** — rejected; mixed history, fragile.
- **Manual takeover** — rejected; the FR1b scope is substantial
  enough that manual implementation would have consumed a lot of
  this session's time budget without a clear win over a corrected
  agent prompt.

### Reversibility

One-way for the abandoned branch (force-overwritten by the new
attempt). The decision itself can't be undone.

---


## ADR-0019 — Pre-seed common ontologies default-on

| | |
| --- | --- |
| Date | 2026-05-12 |
| Status | accepted |
| Reversibility | easy — operators flip a single config key |

**Context.** N1a shipped the internal neosemantics ("n10s") repository
(`SemanticRepositoryType.INTERNAL`) so a casual shepard install has
graph-resident SPARQL without an external triple store
(`aidocs/48`). The N1a graph is bare — annotation pickers and SPARQL
queries resolve only against whatever the operator has manually
imported. On a fresh install that's the empty set, which produces the
"useless on day one" UX that N1a was meant to close.

N1b bundles eight common ontologies (PROV-O, Dublin Core, schema.org,
FOAF, QUDT, OM-2, W3C Time, GeoSPARQL — `aidocs/16` N1b row) into the
classpath at `backend/src/main/resources/ontologies/`, with SHA-256
pinning + an idempotent re-import path
(`OntologySeedService`). The remaining decision is the **default
state** of the seed: on or off.

**Decision.** Default the seed **on**. Operators who prefer a bare-n10s
graph flip
`shepard.semantic.internal.preseed-ontologies.enabled=false`;
operators who want most of the bundle but skip a specific entry use
`shepard.semantic.internal.preseed-ontologies.skip-bundles=qudt,om-2`
(CSV).

**Consequences.**

- Casual users get a useful annotation vocabulary on day one — the
  picker and SPARQL surface land with PROV-O activities, schema.org
  types, QUDT units etc. already resolvable.
- Image-size cost is approximately 13 MB at the full-bundle target
  (N1c CLI). The shipped N1b bundle is a minimum-viable stub
  (~16 KB) carrying canonical IRI prefixes and a handful of
  representative terms per ontology; full canonical content lands
  with the N1c refresh CLI.
- Startup cost is one-off per database — n10s deduplicates by IRI
  via the `n10s_unique_uri` constraint, so the post-first-run
  re-import is a no-op (`triplesLoaded == 0`). The seed service is
  fail-soft per bundle: a missing classpath file, SHA mismatch, or
  n10s call error logs at WARN and the next bundle is attempted.
- Operators with a bare-n10s preference flip
  `shepard.semantic.internal.preseed-ontologies.enabled=false` and
  see the same N1a-shipped behaviour they had before.
- Reversibility is easy: the toggle is a single boolean, the seed
  service is the only post-graph-init writer of `:Resource` nodes
  the bundle introduces, and clearing the seeded vocabulary is a
  Cypher one-liner an admin can run against the database.

**Alternatives considered.**

- *Default off, document the toggle.* Rejected — the "casual user
  fresh install" path is exactly the audience N1a was built for;
  shipping it empty wastes the framing.
- *Ship full canonical ontologies in N1b.* Deferred to N1c. The
  ~13 MB image-size cost is acceptable for the UX win, but bundling
  it via the N1c refresh CLI keeps the canonical-content lifecycle
  separate from the seed-mechanism lifecycle — an operator who
  wants the freshest PROV-O doesn't have to wait for a shepard
  release.
- *Operator-time CLI bootstrap only (no startup seed).* Rejected —
  the "no setup" UX is the value here; an operator who has to run
  a CLI before annotations work is already past the casual-user
  cliff.

## ADR-0020 — shepard is source of truth for HDF container ACLs

**Status.** accepted. **Date.** 2026-05-12.
**Applied in.** A5b (PR #1085); `HdfPermissionBridge` + `HdfAdminRest`.

### Context

A5a (Phase 1) shipped `HdfContainer` create / read / delete with HTTP-Basic
auth between shepard and the HSDS sidecar (per ADR-0012). Phase 2 (A5b)
adds the permission bridge: shepard's per-Collection ACL must be reflected
into the HSDS domain ACL so HSDS-native clients (`h5pyd`, future per-user
auth in A5e) see the same access shape as shepard's REST surface.

The bidirectional alternative — letting operators edit HSDS ACLs out of
band and having shepard reconcile — was considered and rejected.

### Decision

shepard is the **single source of truth** for HDF container ACLs. The
`PermissionsChangedEvent` → `HdfPermissionBridge` flow rewrites HSDS
ACLs on every shepard permission write. Direct HSDS-side mutations get
clobbered on the next shepard change for the affected container.

An admin escape hatch — `POST /v2/admin/hdf/rebuild-acls` — re-derives
every HSDS ACL from shepard's permission graph from scratch, for drift
recovery after manual HSDS edits.

### Rationale

- Single source of truth simplifies the model (one place to look when
  permissions are wrong).
- Eliminates a class of bugs where shepard and HSDS ACLs drift, leaving
  the `h5pyd` user seeing different access than the shepard-REST user.
- The rebuild-acls admin endpoint is the safety valve for operators who
  edited HSDS ACLs before this PR landed.
- Best-effort sync (retry queue, failures don't block user writes)
  keeps shepard's UX intact when HSDS is briefly unavailable.

### Alternatives considered

- **Bidirectional sync** — rejected; introduces conflict-resolution
  rules + a race condition between HSDS-side and shepard-side writes.
- **HSDS-side authoritative** — rejected; would force every shepard
  permission write through HSDS, adding a hard dependency on the HDF
  feature being enabled for permission management on non-HDF
  resources.
- **No sync (operators set ACLs in both places)** — rejected; the
  drift surface is unbounded and a confusing UX.

### Reversibility

Moderate. Reversing requires re-architecting the bridge in either of
the rejected directions; the rebuild-acls endpoint stays valuable
regardless.

## ADR-0021 — GitLab-only adapter in G1b; GitHub + Gitea ship in G1d via the GitAdapter interface seam

**Status.** accepted. **Date.** 2026-05-12.
**Applied in.** G1b (PR #1086); `GitAdapter` + `GitAdapterRegistry` +
`GitLabRestClient`.

### Context

`aidocs/38` (Git integration umbrella) anticipated three host
adapters: GitLab, GitHub, Gitea. The G1b slice ships tracked-artifact
mode (mode b) and needs a reference adapter implementation. Shipping
all three adapters in G1b would have been a big slice.

### Decision

Ship **GitLab only** in G1b. Define a `GitAdapter` interface +
`GitAdapterRegistry` seam now so G1d can drop in `GitHubRestClient`
and `GiteaRestClient` mechanically.

Non-GitLab hosts return RFC 7807 `git.adapter.unsupported-host` 501
until G1d lands. The adapter dispatch is host-substring matching with
a config-driven CSV override (`shepard.git.adapter.gitlab.hosts`) for
self-hosted GitLab on non-obvious DNS names.

### Rationale

- Shipping one adapter end-to-end proves the interface shape; G1d is
  mechanical replication.
- GitLab is the canonical host inside DLR (`gitlab.dlr.de`) — the
  highest-payoff first adapter.
- The 501 response with RFC 7807 makes the unsupported-host case
  user-debuggable without surprises.

### Alternatives considered

- **Ship all three adapters in G1b** — rejected; ~3× the surface
  area for one slice, harder to review.
- **Per-host plugin loading via PayloadKind SPI (`aidocs/47`)** —
  rejected; over-engineering for v1. The `GitAdapter` interface is
  swappable to that shape later without breaking callers.

### Reversibility

Easy. The `GitAdapter` interface seam is internal; replacing
GitLab-only with a different host (or removing the per-host split)
is a refactor with no wire-format implication.

## How to add a new entry

1. Pick the next free `ADR-NNNN` id (chronological — increment from
   the last entry).
2. Add a one-line row to the index table at the top.
3. Append the full entry below the last one, following the same
   `Context / Decision / Rationale / Alternatives / Reversibility`
   shape.
4. Reference the ADR id in the commit message of the change that
   applies the decision: `ADR-0019 in effect`.
5. If the decision references other ADRs (supersedes, couples with,
   etc.), name them explicitly in the body.

If a decision is **overturned**, add a new ADR that supersedes the
old one. Set the old entry's status to `superseded by ADR-NNNN` —
don't edit the body of the old entry.
