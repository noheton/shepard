---
stage: feature-defined
last-stage-change: 2026-05-23
---

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
| ADR-0022 | accepted | 2026-05-13 | OpenAPI client generators: Kiota new baseline + OpenAPI Generator still-maintained legacy (both indefinitely) | moderate |
| ADR-0023 | accepted | 2026-05-13 | Plugin distribution: drop-in JARs into `backend/plugins/` discovered via `ServiceLoader` (not compose-side sidecars, not forked Dockerfiles) | moderate |
| ADR-0024 | accepted | 2026-05-13 | Object-store reference implementation: Garage (replaces MinIO in `infrastructure-local/`); FS1b talks generic S3 via AWS SDK so any S3-compatible endpoint keeps working | easy |
| ADR-0026 | accepted | 2026-05-13 | KIP1d DataCite Member password stored AES-GCM-encrypted (not hashed) on `:DataciteMinterConfig` because the DataCite REST API requires HTTP Basic auth at mint-time; key derives from `shepard.instance.id`. Operator-managed-secret level (not a KMS). | moderate |

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

## ADR-0022 — OpenAPI client generators: Kiota new baseline + OpenAPI Generator still-maintained legacy

**Status.** accepted. **Date.** 2026-05-13.
**Applied in.** `aidocs/57 §4` + §8 question 1; CG1a (Kiota baseline)
and CG1b (OpenAPI Generator legacy maintenance — shipped on
`claude/cg1b-openapi-generator-legacy`).

### Context

`aidocs/57` evaluated client generators and left **§8 question 1**
open: "Kiota vs OpenAPI Generator for `/v2/` — pick a side before
CG1a." The two-shelf API split (`/shepard/api/` frozen-upstream,
`/v2/` fork-development) makes this less binary than it sounds —
the two surfaces can in principle use different generators.

P4c shipped the per-shelf OpenAPI split (`/shepard/doc/openapi/v1.json`
and `/shepard/doc/openapi/v2.json`) which means each generator can
target the exact shelf it's best at without dragging the other along.

### Decision

**Ship both generators indefinitely.** Neither is on a deprecation
path:

- **Kiota** is the **new baseline** for `/v2/` clients. Generates
  against `/shepard/doc/openapi/v2.json` into `clients-v2/<lang>/`.
- **OpenAPI Generator** is the **still-maintained legacy option**
  for `/shepard/api/...` clients. Generates against
  `/shepard/doc/openapi/v1.json` into `clients/<lang>/` (the
  existing path; behaviour-preserving).

The maintainer's stated intent: an operator can keep using either
client generation today and pick whichever fits their tooling. The
secondary framing in `aidocs/57 §4` (originally "primary / secondary")
maps to "new-baseline / legacy-still-maintained" — semantic, not
deprecation.

### Rationale

- **Zero breakage for upstream-API consumers.** Anyone built against
  `dlr-shepard-clients/*` via OpenAPI Generator keeps generating
  the same client. `CLAUDE.md §API-version-policy` covers wire
  shape; this extends the same posture to generator continuity.
- **Best tool per surface.** Kiota's fluent path-builder fits
  `/v2/`'s `appId`-keyed hierarchy beautifully (per `aidocs/57 §4.1`);
  OpenAPI Generator's mature templates are exactly what the
  upstream-frozen surface needs.
- **Two paths shrink the migration story.** Operators don't have
  to choose / migrate / re-test under a "new generator everywhere"
  rollout. They opt in to Kiota on `/v2/` when their use case wants
  it.
- The per-shelf OpenAPI emission (P4c) already makes this trivial
  to wire — each generator reads its own canonical input.

### Alternatives considered

- **Kiota everywhere, deprecate OpenAPI Generator.** Rejected;
  breaks the upstream-compat promise for downstream consumers
  generating against `dlr-shepard-clients`.
- **OpenAPI Generator everywhere, skip Kiota.** Rejected per the
  §4.1 analysis — Kiota's `/v2/` ergonomics + MCP-tool-name
  surfacing (per `aidocs/56`) are a meaningful win on the
  development shelf.
- **Commercial generator (Speakeasy / Stainless / Fern).**
  Rejected per `aidocs/57 §5`; no current value gap justifies
  the licence cost or vendor coupling.

### Reversibility

Moderate. Reversing either half requires regenerating clients
under the other tool and publishing a major-version bump on the
affected package; downstream consumers would need to switch their
generation pipeline. The decision is intended to be permanent —
the cost of switching one side later is real but bounded to the
clients/* tree.

## ADR-0023 — Plugin distribution: drop-in JARs via ServiceLoader

**Status.** accepted. **Date.** 2026-05-13.
**Applied in.** `aidocs/68 §5` question 3 (resolved); UH1a
(`plugins/unhide/` — first plugin under the new shape).

### Context

`aidocs/68 §5` question 3 left three candidate plugin-distribution
shapes open:

1. **Compose-side sidecar** — separate Dockerfile per plugin,
   side-by-side with `backend/`. High operator friction; doesn't
   match the SPI model in `aidocs/47 §2.5`.
2. **JAR drop-in** — plugin JARs into a `backend/plugins/`
   directory, discovered at startup via Java's `ServiceLoader`
   over each plugin's `PluginManifest`. Low operator friction —
   install / uninstall is "copy / delete a file." Requires the
   SPI to be stable enough to be a published surface.
3. **Forked Dockerfile per install** — monolithic image bakes in
   selected plugins at build time. Medium friction; loses
   "install/uninstall without rebuild." Roughly today's shape
   (everything in-tree).

UH1a (the first `shepard-plugin-*` module) is in-flight as the
forcing function — the shape it lands under sets precedent for
every subsequent plugin (`shepard-plugin-hdf-hsds`, `shepard-plugin-video`,
`shepard-plugin-aas`, `shepard-plugin-minter-{epic,datacite}`, …).

### Decision

**Plugins distribute as drop-in JARs into `backend/plugins/`,
discovered at startup via `ServiceLoader`.**

Mechanical shape:

- Each plugin is a standalone Maven module producing a JAR.
- The JAR carries `META-INF/services/de.dlr.shepard.plugin.PluginManifest`
  per Java's `ServiceLoader` SPI contract.
- The backend at startup walks `backend/plugins/*.jar`, adds each
  to a child classloader, and runs `ServiceLoader.load(PluginManifest.class)`
  against it. Each manifest binds its CDI beans / REST resources /
  payload-kind factories through the registries documented in
  `aidocs/47 §2.5` (`PayloadKindRegistry`, future `MinterRegistry`,
  future `AdapterRegistry` family).
- An operator's install path: `cp shepard-plugin-foo-X.Y.Z.jar
  backend/plugins/ && restart shepard`. Uninstall is `rm` + restart.
- The `shepard.plugins.<plugin-id>.enabled=true/false` config key
  per plugin (per `aidocs/47 §2.5`) becomes the runtime opt-out
  without removing the JAR.

The SPI surfaces (`PluginManifest`, `PayloadKind`, `PayloadStorage`,
`FileStorage`, `Minter`, `GitAdapter`, `SemanticConnector`) stay
in core; the implementations live in the dropped-in JARs.

### Rationale

- **Lowest operator friction of the three.** Install / uninstall
  without rebuilding the image is the only shape that matches
  shepard's "plugin-first for new features" CLAUDE.md rule. The
  rule is meaningless if every plugin requires a forked image.
- **Matches the existing Quarkus + ServiceLoader pattern.** Quarkus
  has first-class support for runtime classpath additions via the
  `fast-jar` packaging form's `quarkus-app/lib/` directory; the
  `backend/plugins/` directory is conceptually a sibling that
  shepard's own bootstrap walks.
- **Composes with the `shepard.plugins.<plugin-id>.enabled` runtime
  toggle.** Operators can ship every plugin into `backend/plugins/`
  and gate each one via the admin REST / CLI (`A3b` shape +
  `aidocs/47 §2.5`). The two knobs cooperate: presence of the JAR
  is the install gate, the runtime toggle is the per-install
  on/off.
- **Plugins can ship their own release cadence.** A
  `shepard-plugin-hdf-hsds-1.2.3.jar` and a
  `shepard-plugin-video-0.5.0.jar` can live in the same install
  without their version-bump cycles touching each other. The
  upstream-fork shepard core stays small.
- **Operator clarity on what's installed.** `ls backend/plugins/`
  reads as inventory; the admin REST surface exposes the same
  list via `GET /v2/admin/plugins` (future shape).

### Alternatives considered

- **Compose-side sidecar (option 1).** Rejected — every plugin
  would need its own Dockerfile, ports, volumes, env vars. Doubles
  the install surface area per plugin. Doesn't match the
  in-process SPI model.
- **Forked Dockerfile per install (option 3).** Rejected — losing
  "install/uninstall without rebuild" is the structural cost that
  the plugin-first rule was built to avoid. An operator pulling
  one new plugin should not have to re-bake their image.
- **Single-classloader (no isolation).** Tempting because Quarkus's
  CDI scanning prefers a single classloader; child-classloader
  loading adds complexity. Rejected because two plugins with
  conflicting transitive deps would otherwise fail to coexist;
  the isolation cost is real.

### Reversibility

Moderate. Reversing to "everything in-tree" requires extracting
each shipped plugin's logic back into `backend/` and removing the
`backend/plugins/` discovery hook. Reversing to "compose-side
sidecars" requires per-plugin Dockerfiles + IPC wiring (HTTP /
gRPC) — much bigger lift. The decision is intended to be durable;
the first plugin (UH1a) is the precedent and the SPI surfaces
in `aidocs/47 §2.5` are designed for this distribution mode.

### Forward work flagged by this decision

- ✅ **`PluginManifest` SPI surface** — the `META-INF/services/` shape
  needs the actual `PluginManifest` interface defined in core.
  Lands alongside / immediately after UH1a's first plugin module.
  *Done in PM1a phase 1 (`de.dlr.shepard.plugin.PluginManifest` +
  `PluginContext` + `PluginEntry` + `PluginState`).*
- ✅ **`ServiceLoader`-based bootstrap hook** — runs from
  `ShepardMain.init` after `MigrationsRunner.apply()`, before the
  REST endpoints come up. Pre-existing `aidocs/47 §2.5` design.
  *Done in PM1a phase 1 (`PluginRegistry` observes `StartupEvent`
  which fires strictly after the `@Startup` migrations hook).*
- ✅ **`backend/plugins/` directory in the Dockerfile** — must exist
  as a mount-point that an operator can populate. The
  `infrastructure-local/docker-compose.yml` mounts it as a named
  volume so a `docker cp` is the install action.
  *Done in PM1a phase 2 (image creates `/deployments/plugins/`
  with uid 185 ownership; `SHEPARD_PLUGINS_DIR=/deployments/plugins`
  pinned).*
- ✅ **Admin REST + CLI parity** — `GET /v2/admin/plugins`,
  `PATCH /v2/admin/plugins/<id>` (per `aidocs/47 §2.5`), with
  `shepard-admin plugins {list,enable <id>,disable <id>}`.
  *Done in PM1b (this commit). `PluginsAdminRest` lives in
  `de.dlr.shepard.v2.admin.plugins` (core; one of the
  CLAUDE.md plugin-first exceptions — the runtime SPI registry
  itself stays in-tree). RFC 7396 merge-patch shape on
  `enabled` only; non-`enabled` fields hit a defensive 400
  `plugin.config.read-only-field`; unknown id hits 404
  `plugin.not-found`. CLI mirrors 1:1 with the shared L1
  baseline flags. PROV1a captures each PATCH as an
  `:Activity` row automatically.*
- ✅ **Manifest enrichment + dependency resolution** — the
  `PluginManifest` SPI grows admin-visible metadata
  (`title()` / `description()` / `homepageUrl()` /
  `repositoryUrl()` / `licence()`) and an inter-plugin
  dependency declaration (`dependencies()` returning a list
  of `PluginDependency`); the registry topo-sorts the
  graph and fails plugins fail-soft on missing /
  version-mismatched / cyclic dependencies. *Done in PM1c
  (this commit). All six new methods are `default` so the
  SPI surface stays backwards-compatible — existing plugins
  keep compiling. Version-range parsing handled in-tree by
  `de.dlr.shepard.plugin.VersionRange` (Maven-style syntax;
  no `org.apache.maven:maven-artifact` dependency added —
  same in-tree-parser posture as N1c2's ontology-bundle
  slug parser). `GET /v2/admin/plugins` wire shape grows
  six fields all backwards-compatible additive; CLI table
  grows three width-capped columns; UnhidePluginManifest
  fills every field in (title="Helmholtz Unhide Publish",
  licence="Apache-2.0", deps=empty since UH1a depends on
  in-tree backend SPIs only).*
- ✅ **Documentation** — operator runbook on `docs/reference/plugins.md`
  covering install / uninstall / enable / disable. *Done in PM1a
  phase 3 (this commit; the per-plugin pages at
  `docs/reference/plugins/<plugin-id>.md` per `aidocs/49 §2.2`
  remain a queued follow-up — the UH1a-specific runbook lives at
  `docs/reference/unhide-publish.md` already).*
- ✅ **CLI extensibility SPI** — plugin JARs ship their own
  `shepard-admin` Picocli subcommands via
  `de.dlr.shepard.cli.plugin.AdminCliCommandProvider` +
  `META-INF/services/`, discovered at startup by
  `CliPluginBootstrap`'s ServiceLoader scan of the same plugin
  directory the backend uses. *Done in PM1d (this commit). The
  `unhide` subcommand moved from the in-tree `cli/` module into
  `plugins/unhide/` as the proof-of-shape; end-user UX
  byte-identical (`shepard-admin unhide …` works exactly as
  before). CLI's `with-plugins` Maven profile copies every
  `de.dlr.shepard.plugins:*` `provided` dep into `target/plugins/`
  at packaging time, mirroring backend's profile shape.*
- ✅ **Persist runtime overrides** — the in-memory toggle PM1a
  shipped (`PluginRegistry.runtimeOverrides`) is lost on restart;
  for the "operator-knob" promise to hold (CLAUDE.md "Always:
  surface operator knobs in the admin config") the override must
  survive across restart without an `application.properties`
  edit. *Done in PM1e (this commit). New Neo4j label
  `:PluginRuntimeOverride` (one row per plugin id) with V32
  uniqueness constraint; `PluginRegistry.seedOverridesFromDao()`
  at startup; `PluginRegistry.setEnabled(id, enabled, actorSub)`
  writes through synchronously. Sparse-table semantics — reset
  to deploy-time default DELETEs the row rather than upserts, so
  the table only ever carries rows that actually differ from
  `shepard.plugins.<id>.enabled`. Fail-soft on DAO outage —
  startup falls back to deploy-time defaults; PATCH-time write
  failures keep the in-memory cache current. Matches the
  A3b / N1c2 / UH1a "admin-configurable" idiom with the
  persistent-override variant (per-plugin row rather than a
  global singleton — the natural shape for "one override per
  plugin").*
- ✅ **JAR signature verifier** — operators in security-conscious
  environments need to know a JAR dropped into
  `/deployments/plugins/` is actually signed by a trusted
  publisher. *Done in PM1b2 (this commit). New
  `de.dlr.shepard.plugin.JarSignatureVerifier` `@ApplicationScoped`
  bean that runs before the URLClassLoader is built; uses
  `KeyStore.getInstance("JKS"/"PKCS12")` for trust-anchor
  loading and JDK's `JarFile(verify=true)` for signature
  parsing. Four `shepard.plugins.signing.*` config keys
  (`required=false` default — backward-compatible with PM1a
  shape where bundled plugins ship unsigned). When `required=true`
  an UNSIGNED / UNTRUSTED outcome lands the entry FAILED with
  `failureMessage=plugin.signature.unsigned` /
  `plugin.signature.untrusted` so operators see the cause in
  `GET /v2/admin/plugins`. Operator runbook at
  `docs/reference/plugins.md` §"Signing + compatibility
  enforcement" covers the `keytool` + `jarsigner` workflow.*
- ✅ **Semver-range enforcement of `shepardCompatibility()`** —
  pre-PM1b2 the field was informational; a plugin's declared
  compatibility range wasn't actually checked against the running
  shepard version. *Done in PM1b2 (this commit).
  `PluginRegistry.enforceCompatibility()` runs between discovery
  and dependency-resolution so compat failures pre-empt the
  dependent's `plugin.dependency.missing` (more accurate
  root-cause). Incompatible plugins land FAILED with
  `plugin.compatibility.failed: requires shepard <range>,
  running <version>` (or `plugin.compatibility.unparseable:
  <details>` for a malformed range). New
  `shepard.plugins.compatibility.strict=true` default +
  operator-override valve. `VersionRange.parse` extended with
  npm/Composer-style operator-comma syntax (`>=5.2.0,<6`) —
  unblocks the enforcement because every existing plugin manifest
  writes that shape, not the Maven-bracket form. Three bundled
  plugins (`unhide`, `kip`, `minter-local`) declare
  `">=5.2.0,<6"` and now pass cleanly against the 5.2.x running
  version.*
- **Child-classloader CDI integration** — the "true drop-in"
  workflow where a vendor drops `shepard-plugin-foo.jar` into
  `/deployments/plugins/` and its `@ApplicationScoped` beans +
  `@Path` resources come up without declaring the plugin in the
  backend's `with-plugins` Maven profile. *Deferred to PM1b3 —
  full design doc at `aidocs/platform/69-runtime-plugin-cdi.md`. The PM1b2
  scope-exploration verified against Quarkus 3.27.x source that
  Arc doesn't expose a runtime bean-addition API; the
  structurally correct fix is Option B (extend `PluginContext`
  with `registerHttpRoute(pathPattern, handler)` so plugins ship
  non-CDI Vert.x handlers), which deserves its own slice rather
  than being cargo-culted into PM1b2. PM1b2 ships the
  prerequisites: new `PluginState.DEGRADED` lifecycle state +
  `PluginEntry.markDegraded()` hook (operator visibility in
  `GET /v2/admin/plugins` for "discovered but inert") so PM1b3's
  detection logic has a target state to mark.*
- **ADR-0024** — plugin-distribution sibling decision: object-store
  reference for `infrastructure-local/` (Garage replaces MinIO).
  Same shape of "pick a reference whose OSS posture matches
  shepard's plugin-first / low-friction-quick-start direction."

UH1a's exemption from the full ServiceLoader hookup is lifted —
PM1a phase 3 flipped UH1a to a real `shepard-plugin-unhide.jar`
that ships as a backend Maven `<dependency>` (so Quarkus's
build-time CDI scanner picks the beans) and is also baked into
`/deployments/plugins/` (operator visibility; the PluginRegistry
silently shadows the duplicate). UH1a now follows the standard
drop-in JAR shape that every future plugin will follow.

**Worked example: KIP1g (`aidocs/16` KIP1g).** The second plugin
to land under ADR-0023's shape demonstrates the durability of
the SPI surface against an *extraction* rather than a from-scratch
greenfield. KIP1a had landed the `Minter` SPI + `:Publication`
entity + publish/resolve endpoints all in-tree; KIP1g moved the
HMC-flavoured resolver (`KipResolverRest`) + KIP record JSON-LD
shape (`KipRecordIO`) into a new `plugins/kip/` module while
keeping the `Minter` SPI seam, `:Publication` entity, generic
`POST /v2/{kind}/{appId}/publish` orchestration, and generic
`PublicationIO` wire shape in core. The split tracks CLAUDE.md's
plugin-first heuristic exactly: heuristic #3 ("SPI seams stay
in-tree") keeps `Minter` in core; heuristic #2 ("external
integrations → plugin shape") moves the HMC Kernel Information
Profile record + resolver to a plugin (the HMC spec has its own
release cadence and could be replaced by an alternative
findability protocol without touching the `Minter` SPI or
`:Publication` entity). The wire shape — endpoint path,
JSON-LD body, RFC 7807 problem responses — is byte-identical
to pre-KIP1g; only the source location moved. The
`PublicEndpointRegistry` `.well-known/kip` prefix entry stays
in core for the auth filter's benefit, with the registry's
class Javadoc updated to document the convention that
plugin-contributed public paths are tracked centrally (a
follow-up slice may introduce `PluginContext.registerPublicPrefix(...)`
so plugins self-declare). This is the structural shape every
future "external integration" plugin extraction can copy.

## ADR-0024 — Object-store reference implementation: Garage (replaces MinIO in `infrastructure-local/`)

**Status.** accepted. **Date.** 2026-05-13.
**Applied in.** `aidocs/45 §"Backend matrix"` (FS1b supported-backend
table); follow-up swap of `infrastructure-local/docker-compose.yml`
from `minio/minio` to `dxflrs/garage` will land alongside FS1b (the
SPI doesn't exist yet, so the local stack doesn't actually wire any
object store today). Cited by `aidocs/16` follow-up row.
**Couples with.** ADR-0023 (plugin distribution) — sibling
direction-setting decision: pick references whose OSS posture
matches shepard's plugin-first / low-friction-quick-start
expectation.

### Context

`aidocs/45` (FS1 series) designs the `FileStorage` SPI with two
first-class plugin implementations — `shepard-plugin-file-gridfs`
(default for casual installs) and `shepard-plugin-file-s3` (the
S3-wire-protocol implementation talking generic S3 via AWS SDK v2).
The original design (`aidocs/45 §2.2` C1) named **MinIO** as the
lightweight self-hosted S3-compatible reference for the
`infrastructure-local/` quick-start stack — the same idiom used by
many projects in our neighbourhood. `infrastructure-local/docker-compose.yml`
hasn't yet been wired with an object-store container (FS1b hasn't
shipped), so today's local stack ships neither MinIO nor Garage —
which makes this the cheapest possible moment to revisit the
reference choice before the wire-up lands.

`aidocs/68 §5` flags four still-open plugin-frame questions (Q1 HDF
extraction, Q2 PayloadKind SPI ordering, Q4 convenience-wrapper
shape, Q5 frontend plugins). The MinIO-as-default question lives
under the same frame but wasn't explicit there — this ADR fills
that gap before FS1b lands and bakes the choice in.

The trigger is **MinIO's increasingly defeatured community
edition**. Through late 2025 / early 2026 MinIO has moved
admin-console features (per-tenant policies, identity browsers,
bucket-replication wizards) out of the open-source release into a
commercial-only "AIStor" tier; the AGPL relicensing posture
(AGPL-3.0-only as of v4.x) tightens redistribution constraints for
anyone bundling MinIO into a quick-start stack; community-channel
maintenance signals are mixed. None of this is fatal — operators
running MinIO today are fine, the S3 wire stays compatible — but
it is misaligned with shepard's "low-friction quick-start" goal.
Researchers picking shepard today should not be steered into a
vendor whose OSS posture is degrading.

### Decision

For the `infrastructure-local/` quick-start stack, swap MinIO →
**[Garage](https://garagehq.deuxfleurs.fr/)** as the default
S3-compatible object store. The FS1b SPI implementation (when it
lands) talks the **S3 wire protocol** via AWS SDK v2; any
S3-compatible endpoint — real AWS S3, Cloudflare R2, Backblaze B2,
Wasabi, Ceph RGW, SeaweedFS, MinIO if operators choose — continues
to work without code changes. **Only the local-dev reference
changes.**

The `infrastructure-local/docker-compose.yml` swap itself is a
follow-up tied to FS1b landing (see "Forward work flagged" below);
this ADR locks the direction so FS1b can wire Garage from day one
without re-litigating the reference choice mid-implementation.

### Rationale

- **MinIO posture risk.** Community-edition defeaturing
  (admin-console capability migrating to the commercial "AIStor"
  tier) + AGPL-3.0-only relicensing + mixed community-maintenance
  signals introduce uncertainty that's misaligned with shepard's
  "low-friction quick-start" goal. The local-stack reference
  should not be a vendor whose OSS posture is actively eroding —
  the cost of picking a different reference today is small (one
  compose-file image swap), while the cost of un-picking MinIO
  later (after operators have built muscle memory around it) is
  larger. This is the same rationale shape as ADR-0023's
  plugin-distribution choice: pick the reference that matches
  shepard's structural posture, not the one that's most familiar.
- **Garage matches shepard's profile.** Single-binary (Rust,
  approximately 10 MB), S3 wire-compatible, designed for **small
  geo-distributed clusters** — the same shape as a typical
  research-lab shepard install (one institute, two or three nodes,
  modest TB scale, possibly two campuses replicating). **MIT
  licensed** (Garage core) — no AGPL footgun for operators
  bundling it into their own internal distributions. The
  single-binary + single-config-file deploy story matches the
  `infrastructure-local/` brief ("quick test / evaluation setup"
  per `README.md`) cleanly.
- **Operational simplicity over hyperscale.** Ceph RGW is the
  gold standard at 100-TB+ scale, but the ops surface (Mons, OSDs,
  MGRs, cephx auth, CRUSH maps, placement groups) is wildly out
  of scope for the `infrastructure-local/` quick-start. Garage is
  "one binary, one config file, three nodes optional" — which is
  exactly the right shape for an evaluation deployment. Operators
  who outgrow Garage have a clean upgrade path to Ceph RGW / real
  S3 / managed services without re-instrumenting shepard (the
  AWS-SDK code path is the same).
- **AWS-SDK posture preserved.** FS1b talks **generic S3** via
  AWS SDK v2; the wire compatibility means operators with existing
  MinIO clusters, real AWS S3, Cloudflare R2, Wasabi, Backblaze
  B2, Ceph RGW, or SeaweedFS keep working without changes. The
  choice is **only** about what container image the
  `infrastructure-local/` Compose file pulls when an evaluator
  runs `docker compose --profile tryout up -d` — not about which
  endpoints production shepard supports.
- **Production guidance unchanged.** `aidocs/45`'s amended
  "Backend matrix" continues to recommend "any S3-compatible
  endpoint" and explicitly lists the realistic production options
  (real AWS S3 / Cloudflare R2 / Ceph RGW / Wasabi / Backblaze B2
  / SeaweedFS / Garage / MinIO) without picking a single winner.
  The matrix is the durability surface; the local-stack reference
  is the entry point.

### Alternatives considered

- **Stay on MinIO.** Rejected — see "MinIO posture risk" above.
  Operators today running MinIO are fine; the S3 wire stays
  compatible. But shipping MinIO as the **new-install reference**
  steers researchers into a vendor whose OSS posture is
  actively eroding, which is the opposite of the "comfortable for
  admins" rule. The cost of pivoting later — after `aidocs/45`,
  `docs/admin.md`, and operator muscle memory have all coalesced
  around MinIO — is higher than the cost today.
- **Ceph RGW as the local-stack default.** Rejected —
  operationally overkill. Ceph's ops surface is the right answer
  at multi-tenant 100-TB+ scale, but defeats the purpose of
  `infrastructure-local/` (the README explicitly calls it "quick
  test / evaluation setup"). An evaluator who has to stand up a
  Mon + 3 OSDs + a MGR + RGW just to try shepard's S3 backend is
  not having a quick evaluation. Ceph RGW stays a **first-class
  supported production option** in the matrix; it's just not the
  local-stack reference.
- **SeaweedFS.** Strong contender — Apache 2 licensed, Go-based,
  mature S3 layer, well-maintained, light operational footprint.
  Garage edges it for shepard's profile because Garage's
  **"geo-distributed by design"** matches the research-lab
  pattern (institutions with two campuses, on-prem nodes
  replicating across a low-bandwidth link) better than SeaweedFS's
  **datacentre-first** topology assumption, and Garage's
  single-binary deploy is even simpler. SeaweedFS stays a
  **first-class supported production option** — called out by
  name in the FS1b backend matrix — and an operator who already
  runs SeaweedFS in their environment continues to work without
  changes. The choice between Garage and SeaweedFS for the
  **local-stack default** is genuinely close; we picked Garage for
  the geo-distributed shape match.
- **JuiceFS.** Rejected as the *local-stack* default — JuiceFS is
  a POSIX-on-object-store layer (filesystem semantics over a
  backing store), not the object store itself. The local stack
  needs an actual S3 endpoint for FS1b to talk to, not a layer on
  top. JuiceFS could legitimately sit *behind* shepard's S3 layer
  (operator points shepard at a JuiceFS-backed S3-compatible
  endpoint), but that's an operator-side choice, not a reference
  to ship.
- **No local-stack object store; just GridFS for FS1a.** Rejected
  because FS1b is the slice that proves the SPI works for
  non-GridFS backends; the local stack needs an S3 endpoint to
  test that path before operators wire up real S3 / Garage in
  their own deployments. Shipping FS1a-only would leave the SPI
  half-tested in the reference environment.

### Reversibility

**Easy.** Reversing means restoring `minio` in
`infrastructure-local/docker-compose.yml` (one commit) plus
`aidocs/45` matrix doc edits. No code change anywhere — the FS1b
implementation talks the S3 wire protocol via AWS SDK v2, and the
wire is identical between Garage, MinIO, real S3, R2, B2, Wasabi,
Ceph RGW, and SeaweedFS. The choice is purely about what local
image the quick-start Compose file pulls by default; operators
who want a different reference flip one line in their own
compose override file. The decision is intended to be durable but
costs almost nothing to revisit if Garage's posture itself ever
shifts.

### Forward work flagged by this decision

- **FS1a in-tree `FileStorage` SPI seam** — **✓ shipped.** Lives at
  `de.dlr.shepard.storage.*`; mirror of the `Minter` SPI shape per
  CLAUDE.md plugin-first heuristic #3. `GridFsFileStorage` is the
  in-core default adapter (wraps `FileService`); FS1b's S3 plugin
  slots in alongside without touching `FileContainerService`. See
  `aidocs/16` FS1a row + `aidocs/34` FS1a row.
- **FS1b implementation slice** (designed in `aidocs/45 §3–§4`;
  not yet scheduled). Talks generic S3 via AWS SDK v2; CI tests
  against Garage locally + real S3 in a gated job. The plugin
  shape (`shepard-plugin-file-s3`) lives outside `backend/` per
  ADR-0023 once the PluginManifest SPI lands. Compiles against
  the FS1a SPI; drops in as a sibling adapter.
- **`infrastructure-local/docker-compose.yml` swap** — from
  `minio/minio` to `dxflrs/garage` under the `files-s3` profile
  (per `aidocs/45 §9` FS1d). Small follow-up PR after FS1a/b
  ship; the local stack doesn't actually wire any object store
  today, so the swap lands with FS1b, not before. Tracked in
  `aidocs/16` ("ADR-0024 implementation: swap
  `infrastructure-local/docker-compose.yml` MinIO → Garage" —
  queued, gated on FS1a/b).
- **Operator migration path documented in `docs/admin.md`** —
  anyone running MinIO today is fine: Garage and MinIO speak the
  same S3 wire, so the migration is "stand up Garage, `mc mirror`
  the bucket, repoint shepard's `SHEPARD_FILES_S3_ENDPOINT_OVERRIDE`."
  No data conversion, no schema migration, no client-code change.
  Documented when FS1b's chapter lands in `docs/admin.md`.
- **FS1b plugin's default endpoint config** — when the plugin
  module ships, its packaged `application.properties` should
  default `SHEPARD_FILES_S3_ENDPOINT_OVERRIDE` to point at the
  Garage container in `infrastructure-local/`'s network (`http://garage:3900`),
  matching how the HSDS sidecar's URL defaults today. Operator
  overrides via env vars per AWS SDK v2 conventions.
- **CI matrix entry** — once FS1b ships, the integration-test
  workflow runs the S3 plugin's test suite against a Garage
  container (the reference); the matrix-extended job can
  additionally run against MinIO and LocalStack to keep the wire
  contract honest. Garage is the canary; MinIO + LocalStack are
  the wire-compatibility regression guard.

## ADR-0025 — Minter is optional; default in fresh installs is the LocalMinter plugin

**Status.** accepted. **Date.** 2026-05-13.
**Applied in.** `aidocs/16` KIP1h, `aidocs/34` KIP1h row,
`aidocs/66 §5` (forward-work checklist).
**Couples with.** ADR-0023 (drop-in plugin JARs via
ServiceLoader) — operationalises ADR-0023's plugin-first shape for
the `Minter` SPI; sibling to ADR-0024's "pick references whose OSS
posture matches shepard's low-friction quick-start expectation".

### Context

ADR-0023 + CLAUDE.md heuristic #3 ("SPIs in core, adapters in
plugins") fix the structural direction: cross-cutting interfaces
stay in `backend/`, every implementation ships as a separate Maven
module. KIP1a applied the pattern partially — it shipped the
`Minter` SPI in core but kept the default impl (`MockMinter`) also
in core, paired with a startup fail-fast in `MinterRegistry` that
required at least one matching minter on the classpath.

Two problems with the partial application surfaced once KIP1g
extracted the resolver into `shepard-plugin-kip`:

1. **`MockMinter` violated the plugin-first rule.** The class was
   in `de.dlr.shepard.publish.minter` rather than
   `plugins/minter-local/`. The "Mock" prefix also misled
   operators into thinking it was a test-only fixture rather than
   the legitimate default for fresh installs.

2. **The fail-fast posture broke resolver-only deployments.**
   A researcher who only wants to dereference existing
   `:Publication` rows (operator inherits an instance, runs the
   resolver as part of an archival mirror, etc.) had no way to
   boot without binding a real PID provider. With KIP1g, the
   resolver lives in a different plugin from the minter — but
   the registry's fail-fast tied them together at startup.

The user flagged both in one architectural call: "MockMinter is a
plugin" + "the minter is optional".

### Decision

1. **Rename and extract.** `MockMinter` becomes `LocalMinter` and
   ships as `plugins/minter-local/` (new Maven module mirroring
   `plugins/kip/` shape). The in-core `Minter` SPI seam,
   `MintRequest`, `MintResult`, `MinterException`, and
   `MinterRegistry` stay in `backend/`; only the impl moves.
2. **Relax the registry to optional posture.**
   `MinterRegistry.activeMinter()` returns `Optional<Minter>`. No
   matching minter (or `shepard.publish.minter` unset / `none`) →
   no active minter, registry logs a WARN, the publish endpoint
   returns 503 RFC 7807 `publish.minter.not-installed` on
   demand. The resolver path is unaffected.
3. **Default flips from `mock` to `local`.** Fresh installs run
   with `shepard.publish.minter=local`, matching the
   `shepard-plugin-minter-local` id. Operators who don't want
   publish set `shepard.publish.minter=none` (or leave blank) —
   the resolver keeps working.

KIP1h shipped this triple in one slice, alongside the
versioned-PIDs Phase 1 wire-format change
(`shepard:<instance.id>:<kind>:<appId>:v<n>`).

### Rationale

- **Plugin-first parity.** Every other `Minter` ships as a plugin
  (ePIC = `shepard-plugin-minter-epic`, DataCite =
  `shepard-plugin-minter-datacite`). Treating the default
  identically — rather than carving out an in-tree exception —
  removes the "is this minter special?" cognitive load for plugin
  authors. The same logic ADR-0023 applies to the SPI ↔ adapter
  split.
- **Resolver-only deployments are legitimate.** Operators who run
  shepard as an archival mirror, who only want to host the public
  KIP record for an already-published catalogue, should not need
  a PID provider. The optional posture lets them boot cleanly.
- **Cleaner operator-facing semantics.** "Mock" misled — the
  rename to "Local" honestly reflects that the PIDs are locally
  resolvable, just not registered with a global Handle / DOI
  authority. Operators on the new name don't reach for the
  "is this a real install?" question.
- **`shepard.publish.minter` stays deploy-time-only.** Switching
  the active PID provider mid-flight is unsafe — a Publication
  minted by `local` cannot be dereferenced through the ePIC
  resolver, and vice versa. The "cluster identity / topology"
  CLAUDE.md exception applies (same as `shepard.instance.id`).
  Per-minter knobs (ePIC handle prefix, DataCite credentials)
  still get the runtime `:*Config` shape via the CLAUDE.md
  "operator knobs" rule when KIP1c/d ship.

### Consequences

- **Operators upgrading from KIP1a must change one config value.**
  `shepard.publish.minter=mock` is no longer valid (no plugin
  registers id `mock`); operators set `=local` (or `=none`) before
  upgrade. The `aidocs/34` KIP1h row marks this as **BREAKING**.
- **The `Publication.minterId` field becomes a useful audit
  signal.** Pre-KIP1h every row had `minterId="mock"`; post-KIP1h
  fresh rows have `minterId="local"` and the legacy rows keep
  their `"mock"` stamp. An operator can grep
  `MATCH (p:Publication) WHERE p.minterId = 'mock'` to find every
  pre-upgrade Publication.
- **Build-without-plugins (`-DnoPlugins`) becomes a meaningful
  posture.** Operators building their own backend image without
  the `with-plugins` profile lose the default minter and the
  publish endpoint returns 503 — same posture as KIP1g's resolver
  removal. The pre-KIP1h "the default just works" baseline is gone
  for `-DnoPlugins` builds; operators in that mode must explicitly
  ship a minter plugin or live with the 503.
- **Versioned PIDs Phase 1 (the third leg of KIP1h) shipped in the
  same slice for scope discipline.** The wire-format change from
  `mock:...:<epoch>` to `shepard:...:v<n>` happens once; bundling
  the plugin extract + the optional-minter relax + the
  version-segment change in one slice means one breaking-wire
  change for operators rather than three sequenced ones.

### Forward work flagged

- **CLI parity** — `shepard-admin publish status` showing the
  configured / active minter id is a follow-up; KIP1h shipped the
  core SPI relax but not the CLI surface (the runtime hook is
  deploy-time, so the CLI value is diagnostic-only).
- **Per-minter `:*Config` patterns** — KIP1d shipped this pattern
  for DataCite (`:DataciteMinterConfig` singleton +
  `/v2/admin/minters/datacite/...` + `shepard-admin minters
  datacite ...` CLI parity). KIP1c (ePIC) follows the same shape
  when it lands. See ADR-0026 for the credential-at-rest decision
  KIP1d ratified.
- **ENT1 (versioning Phase 2)** — full `:EntityVersion` graph
  with `previousVersion` / `nextVersion` edges. Wire-shape
  compatible with KIP1h's `Publication.versionNumber` scalar.

## ADR-0026 — KIP1d DataCite Member password is reversibly encrypted, not one-way hashed

**Status.** accepted. **Date.** 2026-05-13.
**Applied in.** `aidocs/16` KIP1d, `aidocs/34` KIP1d row,
`docs/reference/minter-datacite.md` §"Credential at rest".
**Couples with.** ADR-0023 (drop-in plugin JARs — KIP1d is the
third minter plugin), ADR-0025 (Minter is optional; KIP1d slots
into the optional posture).
**Supersedes.** None.

### Context

KIP1d ships the `shepard-plugin-minter-datacite` Minter plugin
that authenticates against DataCite's REST API using HTTP Basic
auth (`user = repositoryId`, `pass = <DataCite Member password>`).
The password is set by the operator via the admin REST / CLI and
must be available at mint-time to construct the HTTP Basic auth
header.

This is a different shape from UH1a's harvest API key, which is
shepard-generated, one-way-hashed (SHA-256), and verified via
constant-time compare on incoming harvester requests. UH1a's key
**must** be hashed because it's shepard's secret-to-keep — the
operator only sees the plaintext exactly once at mint time. KIP1d's
password is **the operator's secret**, shared with DataCite, that
shepard needs to replay verbatim against an HTTPS endpoint.

The question: how do we store the operator-provided DataCite
password so it survives restart, is unreadable in casual Neo4j
inspection (`MATCH (n:DataciteMinterConfig) RETURN n`), and yet
recoverable to plaintext for HTTP Basic auth?

### Decision

Store the plaintext **AES-GCM-256-encrypted** on
`:DataciteMinterConfig.passwordCipher`, with the key derived from
`SHA-256("shepard:KIP1d:datacite:" ‖ <shepard.instance.id>)`
(truncated to 32 bytes). Ciphertext format:
`gcm1:` prefix + base64-url-no-padding of
`IV(12 bytes) ‖ ciphertext ‖ tag(16 bytes)`. The `gcm1:` marker
exists so a future cipher upgrade can recognise legacy shapes.

A SHA-256 hex hash of the plaintext is also stored on
`:DataciteMinterConfig.passwordHash`, surfaced only via its
first-8-hex fingerprint to the admin GET / CLI status output (so
an operator can confirm "yes, that's the password I set" without
exposing material that helps an attacker).

`shepard.instance.id` is already used elsewhere (PROV1a's
`:Activity.instanceId`, KIP1h's `LocalMinter` PID namespace) — KIP1d
reuses it rather than introducing a parallel
`shepard.minters.datacite.cipher-key` key that would need its own
operator-rotation runbook.

### Rationale

1. **The DataCite REST API requires plaintext at mint-time.** HTTP
   Basic auth's wire format is `base64(user:pass)`; there's no
   challenge-response or token-based alternative on DataCite's
   API surface. We can't one-way hash like UH1a does.
2. **Plaintext-at-rest is worse.** A casual Neo4j `MATCH ... RETURN
   n` reveals the password verbatim; an attacker with read access
   to the graph then has full DataCite Member impersonation.
   AES-GCM raises the bar — the same attacker now also needs the
   JVM's `shepard.instance.id`.
3. **`shepard.instance.id` is already an operator-managed secret-ish
   value.** It lives in `application.properties` or an env var;
   PROV1a treats it as part of the audit trail's identity. Reusing
   it for the cipher key matches the existing trust model.
4. **AES-GCM-256 with per-call random IV** is the standard envelope
   for "encrypt this at rest" — authenticated encryption, no
   chosen-plaintext leaks, no nonce-reuse risk.
5. **Operator-runbook honesty.** The reference doc states explicitly
   that this is NOT a KMS — an attacker who reads both Neo4j AND
   the JVM's `shepard.instance.id` recovers the plaintext. KIP1d
   doesn't pretend to be more than it is.

### Alternatives considered

- **Hash the password (UH1a-style)**. Rejected — DataCite's HTTP
  Basic API can't authenticate against a hash. Would force shepard
  to fail every mint until the operator re-enters the plaintext
  via a "mint" endpoint, which is operationally hostile (mints
  must work unattended for the publish UI).
- **Store plaintext, document loudly**. Rejected — bare plaintext
  in Neo4j surfaces in routine queries, logs, and backups. The
  attack surface (anyone with `MATCH ... RETURN n` access) is too
  broad for a credential.
- **External KMS integration (HashiCorp Vault, AWS KMS, Azure Key
  Vault)**. Deferred — KIP1d is the first plugin needing reversible
  credential storage; adding a KMS abstraction layer is premature
  optimisation. A KIP1d-Phase-2 slice can introduce a
  `CredentialBackend` SPI when (a) operator demand materialises,
  or (b) a second secret needs reversible storage (e.g. KIP1c ePIC's
  pre-shared signing key).
- **Per-tenant cipher keys**. Deferred — shepard is single-tenant
  in 5.x; multi-tenant story (`aidocs/63 ADR-0018-or-later` if it
  lands) would introduce the matching credential-isolation slice.

### Reversibility

**Moderate.** The cipher format is forward-versioned (`gcm1:`
prefix) so a future cipher upgrade can co-exist with legacy
ciphertexts during a migration window. Rolling back to plaintext
storage is straightforward but contraindicated.

Two operator-visible consequences:

- **Rotating `shepard.instance.id` post-mint invalidates the
  stored cipher.** Decryption fails loudly with
  `IllegalStateException`; the operator must re-set the password
  via `shepard-admin minters datacite set-password`. This is
  documented in the runbook + the CLI's error message points at
  the rotation operator-action.
- **Backups of `:DataciteMinterConfig` include the ciphertext.**
  Restoring on a different `shepard.instance.id` invalidates the
  password — same operator-action as a rotation.

If a future KMS integration ships under a `CredentialBackend` SPI,
the migration from `gcm1:` to `kms1:` is a one-way decrypt-then-
re-encrypt at startup; the `gcm1:` prefix marker lets the
migration recognise the legacy shape.

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
