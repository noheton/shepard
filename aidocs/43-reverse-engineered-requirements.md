---
stage: feature-defined
last-stage-change: 2026-05-24
audience: contributor + maintainer
purpose: reverse-engineered requirements for argument, not for defence
---

# 43 — Reverse-engineered requirements (to be challenged)

> Companion of [`42-vision.md`](42-vision.md) (prescriptive) and
> [`44-fork-vs-upstream-feature-matrix.md`](44-fork-vs-upstream-feature-matrix.md)
> (progress matrix). This doc is their **descriptive shadow**: the
> requirements the implementation *actually* commits to, inferred from
> the code as of `main` on 2026-05-24, not from any vision or roadmap.

---

## §0 — Reading instructions

Every requirement below is **inferred from the implementation**. The
reader's job is not to nod along; it is to **argue back**. Each row is
written at the just-general-enough level — neither restating a Java
package nor a vacuous "must be useful". Each row carries a
**challenge handle**: the one-line provocation you grab to push back.
If the code is honoured but the doctrine is wrong, throw the row out.
If the code is wrong but the doctrine is honoured, fix the code. Both
adjustments are legitimate outcomes of reading this document.

Each requirement uses this shape:

```
R-NN — <short title>
<one-paragraph statement, generalised>
Evidence: <file:line | endpoint | entity | migration> + <count of independent commitments>
Confidence: STRONG | MEDIUM | WEAK (sentence on what would change it)
Inferred from: EXPLICIT (stated somewhere) | IMPLICIT (only code says this)
Challenge handle: <the one-line provocation to argue back>
```

A requirement is **STRONG** when ≥ 3 independent code surfaces commit
to it (entity + REST + test, or migration + healthcheck + frontend
page). **MEDIUM** is 1–2 surfaces. **WEAK** is one design hint with no
substrate-level enforcement.

---

## §1 — Method

I did this in five passes.

**(1) Inventory.** Walked `backend/src/main/java/de/dlr/shepard/`,
catalogued `/v2/` REST classes (62 distinct class-level paths), Neo4j
migrations (68 Cypher files V1–V63 + rollback pairs), Postgres Flyway
migrations (12 files V1.0–V1.11), application-properties keys (154 of
them), SPI surfaces (`spi/payload`, `spi/ai`, `spi/analytics`,
`spi/api`), shipped plugins (16 modules under `plugins/`), healthcheck
classes (`common/healthz/` — 26 files), and the
`architecture/V2NamespaceTest`. I read the three frozen entity
classes (`Collection`, `DataObject`, `AbstractDataObject`), the
`PayloadKind` and `PluginManifest` interfaces, the `Caddyfile`, and
the `docker-compose.yml` for substrate count.

**(2) Inferred.** For each cluster of evidence I asked: *what is the
most parsimonious requirement that explains why a reasonable engineer
built this?* The answer goes one level above the code — never "the
implementation MUST have a `DataObject.parent` relationship", always
"the implementation MUST express hierarchical decomposition of
DataObjects". If the code only commits to the more specific shape,
that's a *constraint on how* the requirement is met, not the
requirement itself.

**(3) Cited.** Every R-row points back to evidence — a file, a
line range, an endpoint, an entity, a migration ID. Audit findings
from 2026-05-24 (TS, PG/PgBouncer, Mongo, file-routing, Garage,
stack) supplement the code where they surface a deltas between
intent and implementation.

**(4) Generalised.** I rewrote every requirement at the level the
user can argue with. "Shepard MUST treat the Garage S3 bucket as the
sole object substrate" is too specific — that's a deployment choice.
"Shepard MUST be deployable on a single-tenant, on-premise substrate
stack the operator owns end-to-end" is the requirement; Garage is
one realisation of it.

**(5) Flagged.** Each row carries explicit/implicit and a confidence
notch. The contradictions between code and stated design live in
§16 — they are deliberately not buried in the per-row evidence.

**Out of scope.** I did not infer requirements from third-party
library defaults (Quarkus security policy shapes, OGM session
caching) unless they were *actively chosen* by the code. I did not
restate REST shapes the upstream provides — those are upstream's
requirements, inherited byte-for-byte by §R-30.

---

## §2 — Data primitives

The implementation commits to a narrow set of graph primitives that
together model "research data with traceable history". The shape is
visible in `context/collection/entities/Collection.java:19-51`,
`context/collection/entities/DataObject.java:18-46`,
`common/neo4j/entities/AbstractDataObject.java:11-45`, plus 68
Cypher migrations that progressively constrain the graph.

**R-01 — Collections own DataObjects; DataObjects own References.**
The implementation MUST express research data as a three-tier graph
where a Collection contains DataObjects, and DataObjects expose
typed References to Containers in substrate-of-choice payload
stores. The three tiers carry independent permissions, separate
lifecycles, and asymmetric pluggability (References are SPI-extensible;
the Collection/DataObject shapes are frozen).
**Evidence:** `Collection.java:41-51` (`HAS_DATAOBJECT`, `HAS_PERMISSIONS`, `HAS_DEFAULT_FILE_CONTAINER`), `DataObject.java:27-46`, V1__Add_indixes.xml, V4__Add_base_labels.cypher, every `/v2/collections/{appId}/data-objects` endpoint, the entire `context/references/` subtree (3 independent commitments).
**Confidence:** STRONG.
**Inferred from:** EXPLICIT (vision + migration history).
**Challenge handle:** *Why three tiers and not four? Why not treat a
Container as a DataObject of a different kind?* The three-tier shape
forces every plugin into the Reference seam; that has cost.

**R-02 — Every entity carries a UUID v7 application identifier.**
The implementation MUST mint a time-ordered, sortable application-
level identifier for every persistent entity and use it as the
canonical handle on every `/v2/` surface; numeric internal IDs are
treated as substrate accidents and never leak.
**Evidence:** `common/identifier/AppIdGenerator.java:6-29`, 36 of the 68 Cypher migrations are `V##__Add_appId_constraint_*.cypher` (V11 through V59), `HasAppId` marker interface.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT (L2 chain in `aidocs/platform/25`).
**Challenge handle:** *Two identifiers per entity is a substrate
debt — why not collapse to one once `/shepard/api/` is sunset?*

**R-03 — DataObjects form a Predecessor/Successor lineage AND a
Parent/Child hierarchy.** The implementation MUST support two
distinct topologies on the DataObject set: a directed acyclic
**process lineage** (`HAS_SUCCESSOR` / `HAS_PREDECESSOR`) and a
nested **structural decomposition** (`HAS_CHILD` / parent). The two
graphs cross-cut without identifying — a child is not a successor.
**Evidence:** `DataObject.java:30-40`.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT (seed demos exercise both axes).
**Challenge handle:** *Two graphs on the same node set is
expressive but easily confusing — should they be unified under a
typed edge property instead?*

**R-04 — Soft-delete is the default; references survive their
referents conditionally.** The implementation MUST express deletion
as a flag, not a row removal, and MUST honour the
"referenced-data-infinite-retention" invariant: a DataObject with
incoming references cannot be hard-deleted without explicit
admin force.
**Evidence:** `SafeDeleteConflict.java` (v2/integrity), the v2/dataobject DELETE returning 409 on conflict, `feedback_referenced_data_infinite_retention.md` doctrine.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Soft-delete contaminates every query with
`WHERE NOT deleted` boilerplate; a tombstone table would be
cleaner.*

**R-05 — Timeseries channel identity is a 5-tuple, migrating to a
single appId.** The implementation MUST address timeseries channels
by `{measurement, device, location, symbolicName, field}` on the
substrate side, while the planned migration to a single
`shepardId` (per `aidocs/platform/87`) is recognised as a substrate
debt to be paid down without breaking existing readers. The
substrate already carries the `shepard_id` column (V1.11.0).
**Evidence:** `db/migration/V1.11.0__add_shepard_id_to_timeseries.sql`, `v2/timeseries/` REST surface, TS audit AP-9.
**Confidence:** STRONG for "5-tuple is current"; MEDIUM for "single-id is required" (the migration is staged but not yet wired through every endpoint).
**Inferred from:** EXPLICIT.
**Challenge handle:** *5-tuple was an InfluxDB-shaped accident; on
Timescale, why not pick channel identity that matches the substrate
instead of dragging InfluxDB's vocabulary forward?*

**R-06 — DataObject inherits Collection permissions.** The
implementation MUST resolve a user's access to a DataObject by
inheriting from the owning Collection's `:Permissions` node, not by
attaching a permissions node per DataObject. The BUG-148 verdict
crystallised this as Working-As-Intended.
**Evidence:** `Collection.java:47` (`HAS_PERMISSIONS`), `DataObject.java` has *no* `HAS_PERMISSIONS` field, `aidocs/agent-findings/bug-148-do-perms-seeded-2026-05-24.md`.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT (after the BUG-148 contention).
**Challenge handle:** *Per-DO permissions would unlock embargo on a
single test run within an otherwise-open Collection — the inheritance
model precludes that. Is that the right trade?*

**R-07 — Annotations are free-text key-value, with a semantic-
promotion path.** The implementation MUST support free-text
attribute maps on every DataObject (`AbstractDataObject.attributes`,
delimiter `||`) AND MUST allow promoting individual keys into typed
ontology terms via the `:SemanticAnnotation` shape and the
`:Resource` bootstrap (V49).
**Evidence:** `AbstractDataObject.java:21-23`, V3, V10, V49__Bootstrap_internal_semantic_repository.cypher, V56__NOOP_SemanticAnnotation_quantified, V60__NOOP_SHACL_predicates_placeholder.
**Confidence:** MEDIUM (free-text part STRONG; promotion path is more migration-than-runtime).
**Inferred from:** EXPLICIT (semantics chain in `aidocs/semantics/`).
**Challenge handle:** *EAV-on-graph is famously expensive to query
— is the freedom worth the cost?*

**R-08 — Every mutating REST call generates a typed Activity row.**
The implementation MUST capture every state-changing request as a
`:Activity` Neo4j node carrying actor, verb, target appId, and
timestamp; reads are off by default but configurable per instance.
**Evidence:** `provenance/filters/ProvenanceCaptureFilter.java:16-60`, V15__Add_appId_constraint_Activity.cypher, V61__v15_prov_predicates.cypher, `shepard.provenance.capture-reads` config key, `aidocs/agent-findings/prov-resolver-fix-2026-05-24.md`.
**Confidence:** MEDIUM. The *rows* are written; the **PROV-O edges**
(`USED`, `GENERATED`, `WAS_ASSOCIATED_WITH`) are partially wired —
the prov-resolver fix shipped today closes one resolver path but
several edges are still substrate-level TODOs.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Every mutation as a graph node is a
write-amplification choice — would a Postgres append-only audit
table serve PROV-O better?*

---

## §3 — Payload kinds and the SPI seam

The implementation commits to a generic "payload-kind" extensibility
seam — visible in `spi/payload/PayloadKind.java` and the 16 plugins
under `plugins/`. Four kinds are in-tree (file, timeseries,
structured-data, lab-journal); twelve are plugins (hdf5, spatial,
aas, git, video, kip, ai, analytics-ts, unhide, wiki-writer,
importer, file-s3, minter-{datacite,epic,local}, v1-compat).

**R-10 — A payload kind is an addable extension, not a forked
release.** The implementation MUST allow a third party to add a new
payload kind by dropping a META-INF/services entry that resolves to
a `PayloadKind` POJO; the core does not need a rebuild. The
`PayloadKind.entityPackages()` contract drives Neo4j OGM session
construction at startup.
**Evidence:** `spi/payload/PayloadKind.java:11-50`, `plugin/PluginRegistry.java`, V58__AiCapabilityConfig_constraint.cypher (plugin-shipped Neo4j label), every `plugins/*/Manifest.java`.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT (CLAUDE.md "plugin-first" rule).
**Challenge handle:** *PayloadKind is loaded via ServiceLoader
before CDI; that constrains every plugin's startup ordering
forever. Is that worth the extensibility?*

**R-11 — Reference + Container is the contract every kind plays.**
The implementation MUST express every payload kind as a Reference
(graph-side, owned by a DataObject) + Container (substrate-side,
where the bytes live). The Reference carries the kind-specific
identifier; the Container is a substrate-specific home. The
canonical retrieval path is
`/{kind}References/{refId}/payload[/{oid}]`.
**Evidence:** `context/references/` subtree (per-kind reference packages), every plugin's container entity, `aidocs/agent-findings/file-storage-routing-audit-2026-05-24.md`.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Two nodes per payload is genuinely heavier
than one. Why not let plugins flatten the shape when it doesn't add
value (timeseries-references-with-no-container)?*

**R-12 — Substrate-of-choice is per kind.** The implementation MUST
NOT dictate the substrate of a new payload kind. The 16 plugins ship
substrates across S3 (Garage), HSDS (HDF5), Postgres (TimescaleDB),
MongoDB (lab-journal), Neo4j (aas-registration), and git remotes —
each plugin chooses.
**Evidence:** `plugins/{file-s3,hdf5,aas,git,video,kip}/.../*Manifest.java`, `infrastructure/docker-compose.yml`.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Substrate choice per plugin means the
operator footprint grows monotonically — is there an upper bound? §7
says six. Is that real?*

**R-13 — Large payloads stream; uploads are presigned-direct.** The
implementation MUST support chunked-byte upload AND issue presigned
URLs so that browsers and ingest scripts bypass the application for
the actual byte transfer. File payloads ship versioned, with
explicit commit step.
**Evidence:** `/v2/file-containers/{containerAppId}/upload-url` + `/upload-url/commit`, V34__Backfill_FilePayload_providerId.cypher, V21+V23 (FileBundleReference refactor).
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Direct-to-S3 uploads leak the substrate
URL — does that violate §10's "auth perimeter is shepard not
substrate"?*

---

## §4 — Versioning, snapshots, immutability

**R-14 — File payloads keep an append-only version history.** The
implementation MUST preserve every committed version of a file
payload by appendable `PayloadVersion` rows attached to the
container; deletion is metadata-only.
**Evidence:** `/{containerAppId}/files/{originalName}/versions` GET, V41__Add_appId_constraint_PayloadVersion.cypher, `aidocs/44 — PV1a/b rows`.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Append-only without a retention policy means
storage cost grows unboundedly. The Garage audit says no
lifecycle rules exist — is that an oversight or doctrine?*

**R-15 — Snapshots bracket every large transform.** The
implementation MUST express a `:Snapshot` shape that captures a
Collection's state at a moment in time and MUST support diff between
two snapshots. The snapshot-pair doctrine (`project_snapshot_boundaries.md`)
is a procedural commitment, not just a feature.
**Evidence:** V40__Add_appId_constraint_Snapshot.cypher, `/v2/snapshots/{aAppId}/diff/{bAppId}`, `/v2/collections/{collectionAppId}/snapshots/{snapshotAppId}/data-objects`.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Snapshots are clones, not branches —
mutating after a snapshot can't fork the lineage. Should there be
write-side branching?*

**R-16 — Lineage is append-only; predecessors can be added but
not rewritten.** The implementation MUST preserve the directed
edges between DataObjects once committed; corrections to a wrong
predecessor link are themselves Activity rows, not edge mutations.
**Evidence:** `DataObject.java:30-40` edges, the absence of any
PUT/PATCH on predecessor relationships in `/v2/`, `feedback_mutate_after_snapshot.md`.
**Confidence:** MEDIUM — the *code* admits successor mutation; the
*doctrine* forbids it post-snapshot. The gap is unenforced.
**Inferred from:** IMPLICIT (no test enforces it).
**Challenge handle:** *Researchers misclick. A read-only lineage
is operationally cruel. Should we accept correction-as-Activity
instead?*

**R-17 — Migration chains are monotonic and fail-fast.** The
implementation MUST run every Neo4j and Postgres migration in
strict version order, MUST abort startup on any failure, AND MUST
expose its position as a readiness probe. The
`MigrationChainReadinessCheck` reports DOWN until the chain is
quiescent.
**Evidence:** `common/healthz/MigrationChainReadinessCheck.java`, `common/healthz/MigrationChainInspector.java`, `aidocs/agent-findings/ops-migration-healthcheck-2026-05-24.md`, 68 + 12 ordered files.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Fail-fast on any error means a single bad
migration bricks all instances. Is rollback-and-continue ever the
right answer?*

---

## §5 — Provenance + observability

**R-18 — Every interaction with an AI is a typed `Activity`.**
The implementation MUST distinguish 🧑 / 🤖 / 🤝 actor modes on
every mutating Activity row, so that "this annotation came from
Claude, that one came from a human" is queryable. This is the
f(ai)²r differentiator.
**Evidence:** `project_ai_human_collab_provenance.md`, V61__v15_prov_predicates, `plugins/ai/AiPluginManifest.java`, the AI capability config seeded by V58.
**Confidence:** MEDIUM — the *vocabulary* is in place; *enforcement*
that every AI surface writes the actor mode is currently per-plugin,
not policy-tested.
**Inferred from:** EXPLICIT (memory `project_ai_human_collab_provenance.md`).
**Challenge handle:** *Self-reported actor mode is not adversarial-
proof. Should AI-mode capture be on the request envelope (signed),
not the row?*

**R-19 — Every feature must trace across nine surfaces.** The
implementation MUST allow `scripts/trace-feature.sh <ID>` to resolve
a feature row to: aidocs row, design doc, code, REST endpoint,
plugin manifest, frontend page, test, migration, and changelog
entry. A shipped feature missing any one surface is a traceability
bug to fix before any other ship.
**Evidence:** `aidocs/strategy/85-github-project-management-policies.md`, the standing rule in CLAUDE.md.
**Confidence:** MEDIUM — script exists; enforcement is a release-
checklist gate, not CI-enforced.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Nine surfaces is more substrate than most
small features can justify. Is there a "small-feature" exemption?*

**R-20 — Every commit estimates its energy footprint.** The
implementation MUST keep `aidocs/sustainability/00-energy-estimation-log.md`
current with a wattage / CO₂e estimate per material commit. Energy
discipline is a first-class artefact, not an afterthought.
**Evidence:** `feedback_energy_log_per_commit.md`, the file itself.
**Confidence:** WEAK (a doctrine more than a substrate gate — a CI
check would tighten it).
**Inferred from:** EXPLICIT.
**Challenge handle:** *Per-commit estimation is order-of-magnitude
noise; a per-release rollup would be more honest.*

**R-21 — Shepard observes itself in its own timeseries substrate.**
The implementation MUST surface its own runtime counters (request
latency, payload counts, Activity write rate) as timeseries in the
same substrate it offers to researchers — eating its own dogfood.
**Evidence:** `feedback_shepard_measures_itself.md`, `/v2/admin/metrics-summary`, Prometheus + Grafana sidecar in `infrastructure/`.
**Confidence:** MEDIUM (`metrics-summary` exists; full self-host
in TS is partial).
**Inferred from:** EXPLICIT (doctrine).
**Challenge handle:** *Recursive metrics in the operational
substrate means the substrate's failure mode hides itself. Should
ops metrics route to a separate sink?*

---

## §6 — FAIR + research-data-management

**R-22 — Every DataObject carries SPDX license + access-rights
fields.** The implementation MUST express, on every DataObject, an
SPDX licence identifier (or `PROPRIETARY`) and an access-rights
enum (`OPEN`/`RESTRICTED`/`CLOSED`/`EMBARGOED`); both nullable to
support late-stage tagging.
**Evidence:** `AbstractDataObject.java:26-40`, V57__NOOP_AbstractDataObject_fair_fields.cypher, `aidocs/agent-findings/lic1-shipped-2026-05-24.md`.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Nullable means a published dataset can ship
with no licence. Should the publish surface require non-null?*

**R-23 — Every Collection ships citation, ORCID, and metadata-
completeness.** The implementation MUST expose, on the Collection
landing page, an APA/BibTeX/RIS/CSL-JSON citation card, an ORCID
input on the user profile, and a metadata-completeness indicator
that the researcher can act on without leaving the page.
**Evidence:** `/v2/users/{appId}/avatar`, `/v2/admin/users/.../orcid`, RDM-001 + RDM-002 + RDM-005 findings, `frontend/components/context/` (Cite-this dialog), `MetadataCompleteness` widget.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Citation card on every Collection assumes
research-publishability. A pure-internal lab notebook doesn't need
one — is the landing-page assumption universal?*

**R-24 — There is at least one harvest pathway out of the
substrate.** The implementation MUST ship at least one external-
catalogue publishing route. `shepard-plugin-unhide` is the
canonical one; PID minting via DataCite / EPIC is staged behind a
shared `Minter` interface.
**Evidence:** `plugins/unhide/`, `plugins/minter-{datacite,epic,local}/`, `aidocs/integrations/67-unhide-publish-plugin.md`.
**Confidence:** STRONG (Unhide); MEDIUM (Minter; plugin shape
exists, DataCite live-mint not yet in production).
**Inferred from:** EXPLICIT.
**Challenge handle:** *Helmholtz-only harvest is a vendor-lock-in
shaped like federation. Is there a non-Helmholtz fall-back?*

**R-25 — Metadata completeness is a first-class signal.** The
implementation MUST compute and display a metadata-completeness
score per DataObject and per Collection — not just expose the
fields but tell the user what's missing. RDM-005 lives on the
landing page, not buried in admin.
**Evidence:** RDM-005 finding (frontend widget), `/v2/admin/metrics-summary`.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *A score is opinionated — whose definition of
"complete"? Should the operator be able to flip the weights?*

---

## §7 — Multi-substrate operability

The implementation commits to **six substrates** (Neo4j 5,
TimescaleDB on PG16, MongoDB 8, Garage S3, n10s/neosemantics
inside Neo4j, Keycloak) plus PgBouncer (added per the recent PG
audit) and Caddy/Zoraxy at the edge. There is no single-DB
shortcut.

**R-26 — The platform is multi-substrate by design, not by
accident.** The implementation MUST run against a fixed substrate
matrix where each substrate is chosen for fit-to-purpose (graph in
Neo4j, hot timeseries on TimescaleDB, blobs on S3, dense documents
on Mongo, semantic store on n10s, identity on Keycloak), and MUST
NOT collapse to a single substrate even if a vendor offered to.
**Evidence:** `common/healthz/{Neo,Timescale,Mongo,PostGis,*}HealthCheck.java`, `infrastructure/docker-compose.yml`, audit reports for PG/Mongo/Garage all dated 2026-05-24.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Six substrates means six failure modes, six
backup contracts, six upgrade paths. Is the heterogeneity worth its
operability cost?*

**R-27 — Readiness is per-substrate; the gate is AND of all
required.** The implementation MUST expose a readiness probe that
reports DOWN when ANY required substrate is down, AND MUST
distinguish required from optional substrates per `RequiresDatabase`
annotations on resource classes.
**Evidence:** `RequiresDatabase.java`, `RequiresDatabaseFilter.java`, every `*ReadinessCheck.java` in `healthz/`.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Hard-AND readiness means a Mongo blip
takes down the file-upload surface that doesn't need Mongo. Should
the gate be per-route, not per-instance?*

**R-28 — The auth perimeter sits at Shepard, not at the substrate.**
The implementation MUST mediate every substrate access through the
backend's own credentials; users never see substrate connection
strings. Presigned URLs (R-13) are the bounded exception, time-
boxed and per-object.
**Evidence:** Garage access-keys held by backend only, Neo4j `bolt://` not exposed externally, PG accessed only by backend + Flyway, `Caddyfile` route rules.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Single auth perimeter means the substrate-
DDoS attack surface is also the application's. Is that the right
trade?*

**R-29 — Substrate choice can change if the substrate proves
wrong.** The implementation MUST treat substrate selection as
revisable: the migration chain (R-17) + the per-substrate
healthcheck (R-27) + the auth perimeter (R-28) together let an
operator swap a substrate without re-issuing client tokens. The
"we can change DBs" framing in recent conversations is operative.
**Evidence:** MinIO→Garage replacement (ADR-0024), the staged TimescaleDB→single-channel-appId migration, PgBouncer added after the substrate was already live.
**Confidence:** MEDIUM (the substrate-swap path exists for new
adopters; in-place swap on a running instance is operationally
hard).
**Inferred from:** EXPLICIT.
**Challenge handle:** *Substrate-swap optionality is a luxury that
costs in abstraction. Is the optionality real, or aspirational?*

---

## §8 — API + interoperability

**R-30 — `/shepard/api/*` is byte-stable against upstream 5.2.0.**
The implementation MUST preserve the wire shape of every upstream
endpoint such that a client built against
`gitlab.com/dlr-shepard/shepard@5.2.0` keeps working unchanged.
Bug fixes preserve the shape; new behaviour goes to `/v2/`.
**Evidence:** `architecture/V2NamespaceTest.java:126-153` enforces this, `backend/src/test/resources/fixtures/v5/openapi-5.4.0.json` (v5 corpus), CLAUDE.md "API-version policy".
**Confidence:** STRONG (test-enforced).
**Inferred from:** EXPLICIT.
**Challenge handle:** *Byte-stability indefinitely is a debt that
compounds. When does it end?*

**R-31 — All new endpoints land under `/v2/`.** The implementation
MUST mount every new REST resource at a path prefix `/v2/`,
including plugin-shipped resources (`de.dlr.shepard.plugins..` is
an equal-tier `/v2/` tenant). This is architecturally enforced.
**Evidence:** `V2NamespaceTest.java:88-124`, 62 distinct `/v2/` class-level paths catalogued.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *`/v2` is a versioned shelf — what's `/v3`?*

**R-32 — OpenAPI is the contract.** The implementation MUST emit an
OpenAPI specification at `/shepard/doc/openapi/...` and MUST keep
it accurate enough that the generated client (Python +
TypeScript) is the canonical SDK.
**Evidence:** `quarkus.http.non-application-root-path=/shepard/doc`, `backend-client/`, `clients-v2/`, OpenAPI annotations on every v2 resource.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Generated clients are bulky; what if a
hand-written SDK would be better DX?*

**R-33 — MCP is a first-class surface, not an experiment.** The
implementation MUST expose its content over MCP (Model Context
Protocol) on `/v2/mcp/sse` such that a Claude or compatible client
can discover, search, and read Shepard content without a separate
adapter layer.
**Evidence:** `application.properties` MCP block, `v2/mcp/` package, `aidocs/platform/30-mcp-plugin-design.md`.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *MCP commits to Anthropic's protocol —
proprietary today. Is that a portability concession worth
making?*

**R-34 — Every UI action is a callable API.** The implementation
MUST express every user-facing capability as a REST endpoint, so
the frontend is a consumer of the API like any other client. UI-
only features are an anti-pattern.
**Evidence:** `feedback_ui_api_parity.md`, the frontend's exclusive use of generated clients.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT (doctrine).
**Challenge handle:** *UI-side composition (multi-call client
flow) is now untracked work. Should some flows be server-collapsed
into single endpoints?*

**R-35 — Pagination + error envelopes are shaped identically across
all v2 endpoints.** The implementation MUST use a single `page`/`size`
parameter pair and a single error JSON envelope across the entire
`/v2/` surface.
**Evidence:** every list endpoint in `v2/*/resources/`.
**Confidence:** MEDIUM — the recent API scrutiniser audit flagged
inconsistencies; the doctrine is honoured ~80%.
**Inferred from:** IMPLICIT.
**Challenge handle:** *Cursor-pagination would scale better at MFFD
size — is page-based the right choice?*

---

## §9 — Plugin + extensibility

**R-36 — New features default to plugins, not in-tree.** The
implementation MUST treat in-tree code as the exception, not the
rule — for new payload kinds, new external integrations, new
storage backends, and new domain features that fit a clean SPI
seam. The CLAUDE.md "plugin-first" rule is normative.
**Evidence:** 16 plugins under `plugins/`; the 4 in-tree kinds
(file, timeseries, sd, lj) are upstream-derived; the SPI doc
`aidocs/platform/47`.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Plugin-first means every feature pays
classloader + ServiceLoader + manifest tax. Does that disincentive
small good ideas?*

**R-37 — Plugins ship their own docs in their own module.** The
implementation MUST require each plugin to provide
`plugins/<id>/docs/{reference,quickstart,install}.md` as a
condition of merge.
**Evidence:** CLAUDE.md "plugins ship their own documentation"
block, `aidocs/agent-findings/plugin-docs-gap-audit-2026-05-23.md`.
**Confidence:** MEDIUM (doctrine; the gap-audit shows several
plugins fail this today).
**Inferred from:** EXPLICIT.
**Challenge handle:** *Three docs per plugin is a high bar — is
one good README enough for a tiny plugin?*

**R-38 — Plugins declare their own sidecars.** The implementation
MUST allow a plugin manifest to declare the docker-compose
sidecars it needs (Garage for file-s3, HSDS for hdf5, etc.) such
that compose assembly is derivative of plugin set, not bespoke per
operator.
**Evidence:** `plugin/SidecarSpec.java`, `plugin/SidecarsAssembler.java`, PM1f shipped per `feedback_plugins_declare_sidecars.md`.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Compose-assembly-from-plugins is brittle to
substrate version drift; is helm-chart generation the better
shape?*

**R-39 — The SPI is stable once published; deprecation, not
deletion.** The implementation MUST treat every SPI interface
(`PayloadKind`, `PluginManifest`, `Minter`, `GitAdapter`,
`SemanticConnector`, the planned `EmbeddingProvider` /
`NotificationProducer` / `StorageProvider`) as a public contract;
new methods get default implementations, old methods get
`@Sunset` annotations, never removal.
**Evidence:** `spi/api/Sunset.java`, `spi/api/SunsetFilter.java`, the SPI doc.
**Confidence:** MEDIUM (the policy is wired; enforcement is
case-by-case).
**Inferred from:** EXPLICIT.
**Challenge handle:** *Indefinite stability of an SPI calcifies
mistakes. Is there a sunset horizon?*

---

## §10 — Security + compliance

**R-40 — Auth is OIDC-first, API-key-coexisting, MCP-distinct.**
The implementation MUST mediate every protected request through
one of: a JWT issued by the institute's OIDC provider, an API key
the user generated and stored as a credential, or the MCP-specific
auth filter. Anonymous read may be allowed per-Collection.
**Evidence:** `auth/` package, JWT filter, API-key filter,
`McpAuthFilter` (the application-properties note explains why this is
distinct from OIDC), `feedback_use_v2_for_operations.md`.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Three auth modes triples the attack surface.
Could MCP ride OIDC-client-credentials instead?*

**R-41 — RBAC carries permissions inheritance.** The implementation
MUST resolve user access via Keycloak roles + Collection-level
`:Permissions` nodes that DataObjects inherit. `instance-admin` is
a special role granting `/v2/admin/*` access.
**Evidence:** `auth/permission/` subtree, every `@RolesAllowed`
annotation on `/v2/admin/*` endpoints.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Inheritance-only permissions can't model
"this NCR is restricted within an open Collection". §R-06 same
challenge.*

**R-42 — Dependencies are licence-gated; six SAST/SCA gates are
green-required.** The implementation MUST refuse any direct
dependency under GPL/AGPL/SSPL (the dependency-review gate enforces
this), AND MUST keep SpotBugs+findsecbugs, CodeQL, OWASP Dep-Check,
Trivy, gitleaks, and dependency-review green on every PR.
**Evidence:** CLAUDE.md "security gates" block, `.github/workflows/{backend-ci,security,build-images,codeql}.yml`, `.github/dependency-review-config.yml`.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Banning AGPL excludes some excellent
research-data libraries (e.g. GraphDB). Worth the loss?*

**R-43 — Every release ships an SBOM.** The implementation MUST
attach a CycloneDX SBOM to every published GHCR image and every
GitHub release.
**Evidence:** `build-images.yml` `anchore/sbom-action` step,
release tagging.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *SBOM-without-vuln-watch is theatre. Is the
SBOM actually consumed by anyone?*

---

## §11 — Operability

**R-44 — Every operator knob is runtime-mutable.** The
implementation MUST surface every feature toggle, integration
credential, retention window, and cap as a runtime-mutable
admin REST endpoint (`PATCH /v2/admin/<feature>/config`) with CLI
parity via `shepard-admin`. Deploy-time-only properties are the
documented exception (cluster identity, pre-startup ordering, internal buffer sizes).
**Evidence:** `:FeatureToggleRegistry` + `/v2/admin/features`, `:SemanticConfig` + `/v2/admin/semantic`, `:UnhideConfig` + `/v2/admin/unhide/config`, `:AiCapabilityConfig` (V58), `:InstanceConfig` (V59), `:DataciteMinterConfig` (V33), `:UnhideConfig` (V30), `:InstanceRorConfig` (V42), `:SqlTimeseriesConfig` (V43), CLAUDE.md "surface operator knobs" block.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Runtime-mutable config without a config-
change Activity audit is dangerous. PROV1a addresses this — is the
coverage actually 100%?*

**R-45 — CLI parity with the admin REST surface is mandatory.**
The implementation MUST expose every admin REST capability via a
`shepard-admin` subcommand, so an operator with shell access can
run the platform without a browser.
**Evidence:** `cli/` package, `shepard-admin` binary, CLAUDE.md
"CLI parity" rule.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Twin surfaces double the test load. Could the
CLI be a thin REST-call wrapper that the test suite ignores?*

**R-46 — Smoke runs after every deploy; redeploy is a wired
target.** The implementation MUST wire `make redeploy` to chain
wait-for-health + smoke per the recent ops bundle.
**Evidence:** `infrastructure/smoke-test.sh`, the Makefile, the
recent `chain wait-for-health + smoke into redeploy targets`
commit (29a0391d).
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Smoke-as-redeploy-gate slows iteration; is
there a fast-path for backend-only changes?*

**R-47 — Backup contract is a known gap.** The implementation
*does not currently* commit to a backup contract for any
substrate. The PG and Garage audits both flag this as missing. The
implicit requirement is: **a backup contract MUST exist**; the
explicit state is that it doesn't.
**Evidence:** Garage audit "no lifecycle rules", PG audit "no
declared backup", absence of `infrastructure/backup/`.
**Confidence:** WEAK (this is a *violated* implicit requirement —
the implementation is silent on backup, which is itself a
problem).
**Inferred from:** IMPLICIT.
**Challenge handle:** *Backup is the most important thing the
platform doesn't do. Defer or stop everything?*

---

## §12 — UX + accessibility

**R-48 — Advanced mode is a strict superset of basic.** The
implementation MUST never hide in advanced mode a control that
appears in basic mode; `v-if="!advancedMode"` is structurally
wrong.
**Evidence:** `frontend/composables/context/useAdvancedMode.ts`,
`feedback_basic_advanced_superset.md`.
**Confidence:** STRONG (doctrine).
**Inferred from:** EXPLICIT.
**Challenge handle:** *Superset means advanced is always heavier.
Is there a mode that exposes power without the cognitive cost?*

**R-49 — UI must validate at 4K viewports.** The implementation
MUST test every UI surface at the user's real 4K viewport, not just
1440p; the BUG-139 finding crystallised this.
**Evidence:** `feedback_validate_user_viewport.md`, the recent UI
regression suite touches.
**Confidence:** MEDIUM (doctrine; CI-enforcement partial).
**Inferred from:** EXPLICIT.
**Challenge handle:** *4K is the unicorn — is 1440p actually the
common case at DLR?*

**R-50 — Every payload kind has a task page AND a reference page.**
The implementation MUST ship `docs/help/*.md` (task) and
`docs/reference/*.md` (reference) for every shipped feature, in
the same PR.
**Evidence:** CLAUDE.md "user-facing docs" block, `aidocs/ops/49`.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Two docs per feature doubles the review
load. Could one structured doc with both audiences work?*

**R-51 — Contextual help is in-app, not external.** The
implementation MUST serve `docs/help/` and `docs/reference/` from
the running Shepard instance at `/help`, not require a separate
Pages site.
**Evidence:** `frontend/utils/helpMarkdown.ts`, `aidocs/ops/49-in-app-user-docs.md`, `/help` route.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *In-app help means a doc fix ships in the
image. Is that the right release cadence?*

---

## §13 — Sustainability + responsibility

**R-52 — Local AI is the default; external providers are opt-in.**
The implementation MUST default to SAIA/GWDG (DLR-internal AI
inference) over external commercial providers; users opt in to
external explicitly via per-feature config.
**Evidence:** `project_ai_plugin_config.md`, `plugins/ai/`,
`FLO_AI_KEY` env var pattern, V58__AiCapabilityConfig.
**Confidence:** MEDIUM (doctrine; enforcement depends on operator
config).
**Inferred from:** EXPLICIT.
**Challenge handle:** *SAIA is currently weaker than GPT-5/Claude
on long-context. Is "default local" the right default or the right
constraint?*

**R-53 — Industrial IP stays inside the operator's perimeter.**
The implementation MUST permit operator policy that no DataObject
content (timeseries, files, annotations) leaves the institute
network without explicit admin opt-in.
**Evidence:** R-28 + R-52 + R-44 together (auth perimeter, local
AI, runtime-mutable opt-in toggles).
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *IP isolation undermines the f(ai)²r
external-AI typed-Activity story. Is the policy contradictory?*

**R-54 — Regulatory alignment is a stated alignment, not a
certification.** The implementation MUST track alignment with
EU AI Act Art-50 disclosure, EASA AI guidance, ISO 10303 AP242,
DIN EN 9100, FAIR principles, CHAMEO, Material OWL — without
claiming certification.
**Evidence:** `aidocs/strategy/` subtree, `feedback_ai_human_collab_provenance.md` ("closes EU AI Act Art-50 gap"), CLAUDE.md.
**Confidence:** MEDIUM (alignment is documented; substrate
enforcement is partial).
**Inferred from:** EXPLICIT.
**Challenge handle:** *Alignment-without-certification is a
positioning claim, not a property. Is that honest?*

---

## §14 — Cross-cutting NFRs

The implementation commits to non-functional shapes implied by
its indexes, partitions, healthcheck cadences, and the MFFD-scale
target stated in memory (~17K DOs, ~12K files, ~280K Activity
rows, ~2.6 GB TS).

**R-55 — TimescaleDB hypertable holds compressed, time-partitioned
samples.** The implementation MUST support write throughput of
≥ 10K samples/sec sustained on a single channel with compression
on, and query latency p99 < 200ms for a chart-view window of 60s.
**Evidence:** V1.4 compression, V1.8 optimisation, the TS audit's
note that COPY for batches > 1K rows would help, the chart-view
endpoint.
**Confidence:** MEDIUM (the *targets* are not in code — but the
substrate choices commit to making them achievable).
**Inferred from:** IMPLICIT.
**Challenge handle:** *No SLA published — would publishing one be a
trap?*

**R-56 — Healthcheck cadence is sub-30-second.** The implementation
MUST refresh per-substrate liveness within 30 seconds
(`shepard.health.readiness.max-staleness=PT30S`).
**Evidence:** `application.properties:24-28`.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *30s staleness on a six-substrate readiness
gate means up to 3 minutes between a substrate failing and the
last pinger noticing. Is that good enough?*

**R-57 — Recovery is best-effort: scheduler retries every 15
seconds.** The implementation MUST run a background scheduler that
re-pings stale required substrates every 15 seconds and flips
readiness back to UP when they recover.
**Evidence:** `DbRecoveryScheduler.java`, `shepard.health.recovery.interval=PT15S`.
**Confidence:** STRONG.
**Inferred from:** EXPLICIT.
**Challenge handle:** *Auto-recovery hides intermittent failure
without alerting — does the platform commit to also notifying
operators?*

---

## §15 — Architectural MUST-NOTs

The negations the implementation commits to, by virtue of what it
won't do.

- **MUST NOT** break `/shepard/api/*` byte stability (R-30).
- **MUST NOT** add a fourth substrate where a schema split would do
  (the recent PG audit's antipattern position; the synthesis T2
  argument).
- **MUST NOT** hard-delete referenced data (R-04).
- **MUST NOT** skip a payload during import (`feedback_completeness_nonnegotiable.md`).
- **MUST NOT** mutate destination data mid-ingestion (`feedback_mutate_after_snapshot.md`).
- **MUST NOT** depend on GPL/AGPL/SSPL (R-42).
- **MUST NOT** hide a basic-mode control in advanced mode (R-48).
- **MUST NOT** ship a user-visible feature without backend + frontend
  + tests + docs (R-50, `feedback_done_criteria.md`).
- **MUST NOT** mount a new endpoint outside `/v2/` (R-31).
- **MUST NOT** leak Neo4j numeric IDs through `/v2/` responses (R-02).

---

## §16 — Contradictions between code and stated design

The list of places where the vision and the substrate diverge.

**C-1 — Vision says Activity captures every mutation; code only
writes rows.** `aidocs/42-vision.md` and `42-vision §provenance`
imply full PROV-O typed-edge capture. The
`ProvenanceCaptureFilter` writes rows; the `USED` / `GENERATED` /
`WAS_ASSOCIATED_WITH` edges are partial — the prov-resolver fix
shipped today closes one path, but the substrate audit shows
several edges are still TODO. *(Lowers R-08 to MEDIUM.)*

**C-2 — Vision says "publish to Helmholtz Unhide"; the harvest
pathway is one-way and operator-configurable but the live mint to
DataCite is staged.** Unhide is wired; DataCite/EPIC minters
shipped as plugin manifests with config singletons (V33, V45) but
the live PID-mint loop is not on the happy path of every
publication.

**C-3 — `aidocs/44` marks several features `✓ shipped` where the
substrate evidence is mixed.** Examples per the day's audits: TS
shapes for chart-view (no continuous aggregate per AP-6),
file-payload routing (singleton-bypass path per file-storage
audit), Mongo backup (no contract per Mongo audit).

**C-4 — Doctrine says "advanced is strict superset of basic"; the
recent UI audits surface several `v-if="!advancedMode"`
violations.** Doctrine ≠ enforcement; a CI rule would catch the
violations.

**C-5 — Doctrine says "plugins ship their own docs"; the
plugin-docs-gap-audit lists several without quickstart or install
pages.** Same pattern as C-4.

**C-6 — `DataObject` has no `:Permissions` node, yet the docs and
vision both spoke of per-DO permissions until BUG-148.** The
implementation chose inheritance from Collection (R-06); the
prior vision text needs updating in `aidocs/42`.

**C-7 — The 5-tuple TS identity (R-05) coexists with the staged
single-`shepardId` migration (V1.11.0) — both are "current" in the
read path.** The migration is partway. Until R-05 collapses to one
identity, code and docs each tell half the story.

**C-8 — Doctrine says "energy-per-commit estimated" (R-20); the log
exists but is uneven across commits.** The substrate (the log
file) commits to less than the doctrine asks for.

---

## §17 — Generalisable provocations

These are the ten requirements rephrased at the **most general**
level — the level at which the user can argue back without
descending into code. Each is a sentence you can grab and beat the
implementation over the head with.

**P-1.** *Shepard MUST be a multi-substrate research-data platform —
no single-DB shortcut — bounded at six substrates (Neo4j 5,
TimescaleDB on PG16, MongoDB 8, Garage S3, n10s, Keycloak).*
Alternative: collapse to Postgres + Neo4j only, accept the loss of
HSDS / Mongo / S3 fit-to-purpose, gain operability. Cost of
abandoning: every plugin's substrate choice (R-12) must re-prove
itself; the heterogeneity tax goes away but each kind pays a
shoehorning tax instead. Evidence: §7.

**P-2.** *Shepard's wire surface MUST stay byte-stable against
`gitlab.com/dlr-shepard@5.2.0` indefinitely (the per-operator sunset
policy is the bounded exception).* Alternative: cut a 6.0 with
no compat shim, accept a real upstream split, gain freedom to
reshape the surface. Cost: every upstream-deployed institute either
freezes on 5.x or migrates en masse. Evidence: R-30.

**P-3.** *Shepard MUST capture every state-changing interaction as
a typed PROV-O `:Activity`, including AI-mode (🧑 / 🤖 / 🤝)
disclosure on every row.* Alternative: log to syslog and call it
audit; accept the loss of structured retrospective queries. Cost
of abandoning: f(ai)²r differentiation collapses; EU AI Act Art-50
disclosure becomes a side document. Evidence: R-08, R-18.

**P-4.** *Shepard's plugin SPI MUST let a third party add a payload
kind, a storage backend, a notification producer, a minter, or a
git adapter without forking the core.* Alternative: in-tree
everything, accept the integration debt, gain one-codebase
clarity. Cost: every new payload kind shipped by a non-core team is
either a fork or doesn't happen. Evidence: R-10, R-36.

**P-5.** *Shepard's UI MUST be the result of API calls, never the
source of capabilities — every user action MUST be reproducible
from a shell.* Alternative: let the UI ship server-coupled
features (the "monolithic web app" stance); gain integration
speed; lose CLI/MCP/script automation. Cost of abandoning: the
digital-native persona (Role 10) gives up. Evidence: R-34, R-45.

**P-6.** *Every operator knob MUST be runtime-mutable via admin
REST + CLI parity; deploy-time-only properties are the documented
exception, not the default.* Alternative: restart-to-flip
everything; gain config-as-code purity. Cost: institute operators
who run shepard with no CI pipeline (most of them) lose ergonomic
parity. Evidence: R-44, R-45.

**P-7.** *Shepard MUST express research data as a three-tier graph
(Collection / DataObject / References → Containers) with two
distinct edge topologies on DataObjects (lineage + hierarchy).*
Alternative: collapse to one tier and one edge topology — pure
graph soup, plugin-typed. Cost: every helper, every frontend
page, every audit script presupposes the three-tier shape. The
migration would be Ship-of-Theseus. Evidence: R-01, R-03.

**P-8.** *Shepard MUST default to local AI (SAIA / GWDG) and treat
external commercial providers as per-feature, per-tenant opt-in,
with f(ai)²r capture on every interaction.* Alternative: pragmatic
"use the best model" default. Cost of abandoning: IP isolation
(R-53) and regulatory positioning (R-54) both weaken. Evidence:
R-52.

**P-9.** *Shepard's docs MUST live with the code they describe —
plugin docs in plugin modules, in-app `/help` served from the
running image, casual + reference pages required in the same PR
as the feature.* Alternative: a separate docs repo at a separate
cadence; gain editor-friendliness; lose currency. Cost of
abandoning: the structural fix for screenshot/feature drift goes
away. Evidence: R-50, R-51, R-37.

**P-10.** *Shepard's identity primitives (Collection / DataObject /
appId) are frozen; everything else is pluggable.* Alternative:
allow plugins to introduce a new top-level identity primitive
(e.g. a `:Workflow`). Cost: the simple mental model goes; the
substrate becomes shapeless. Evidence: R-01, R-02; CLAUDE.md
"plugin-first" exceptions list.

---

## §18 — What is NOT a requirement (the negations)

Equally important: the things the implementation does NOT commit
to, so the reader doesn't accidentally treat them as requirements.

- **Shepard is NOT a wiki/CMS/publishing platform.** It can publish
  via plugins (`unhide`, `wiki-writer`), but the core does not
  commit to a publishing-platform shape.
- **Shepard is NOT a workflow engine.** There is no DAG executor,
  no scheduler beyond the recovery pinger, no time-trigger surface.
- **Shepard is NOT a notebook environment.** The Jupyter
  integration (`aidocs/integrations/81`) is one-way data export, not
  bidirectional cell sync.
- **Shepard is NOT a vendor-neutral MES.** Manufacturing-quality
  features are partial (no NCR primitive, no calibration cert
  link, no rework-loop edge type — per the persona-manufacturing-
  quality audit).
- **Shepard is NOT a real-time control system.** Activity is
  eventually-consistent; ingest is best-effort; the auto-recovery
  scheduler (R-57) makes this explicit.
- **Shepard is NOT a vendor lock-in.** Multi-substrate (R-26),
  plugin-first (R-36), OpenAPI (R-32), MCP (R-33), and CLI (R-45)
  all hedge against substrate or platform lock.
- **Shepard does NOT commit to a backup contract.** R-47 flags this
  as a known violated implicit. It is *not* in the system today; do
  not assume it is.
- **Shepard does NOT enforce ontology compliance.** Annotations
  remain free-text (R-07); semantic promotion is opt-in.

---

## §19 — Decisions log (per section)

| § | Strongest evidence | Most uncertain inference | Most important contradiction |
|---|---|---|---|
| §2 Primitives | 36 appId-constraint Cypher migrations | R-16 lineage immutability is not test-enforced | C-6 — vision said per-DO perms, code says inherit |
| §3 Payload kinds | 16 plugins + `PayloadKind` SPI | R-12 substrate-per-kind has no ceiling | C-3 — `aidocs/44` overclaims TS chart-view shapes |
| §4 Versioning | V40, V41, snapshot+diff REST | R-15 snapshot-vs-branch is a missing feature | none material |
| §5 Provenance | `ProvenanceCaptureFilter` | R-18 actor-mode enforcement is per-plugin | C-1 — PROV-O edges partial |
| §6 FAIR | LIC1 shipped, RDM-005 widget | R-22 license nullability allows uncited publish | none material |
| §7 Substrates | 6 healthcheck classes + audits | R-29 in-place swap is aspirational | C-3 in PG/Garage audit specifics |
| §8 API | `V2NamespaceTest` enforces R-30/R-31 | R-35 pagination uniformity is ~80% | none material |
| §9 Plugins | 16 manifests, SidecarsAssembler | R-37 doc-completeness gap on several plugins | C-5 — plugin-docs gap audit |
| §10 Security | 6 CI gates wired | R-40 three-auth-mode surface area | none material |
| §11 Operability | 9 `:*Config` singletons + CLI | R-47 backup contract MISSING | C-3 — Mongo no backup audit |
| §12 UX | help routes + reference pages | R-49 4K enforcement partial | C-4 — advancedMode supersettess |
| §13 Sustainability | energy log + local-AI default | R-20 per-commit estimation uneven | C-8 — log gaps |
| §14 NFRs | healthcheck timing config | R-55 throughput target unstated | none material |

---

## §20 — Suggested follow-ups

A handful of dispatches the user might pull next.

- **"Non-requirements review" dispatch.** Take §18 and challenge each
  negation: is Shepard really *not* X, or is it accreting toward X
  without admitting it? The wiki/CMS line is the most contestable
  (wiki-writer plugin exists).
- **Persona-by-persona requirements-fit audit.** Walk each persona
  in `aidocs/agent-findings/persona-*` against this doc; which
  requirements they need that we don't commit to, which requirements
  we commit to that they don't need.
- **Architecture-test backward walk.** Read every assertion in
  `backend/src/test/java/de/dlr/shepard/architecture/` and add the
  invariants it enforces to this doc — they often capture
  requirements no aidocs doc states.
- **"What if we deleted plugin X" thought experiment.** For each of
  the 16 plugins, what requirement does its removal break? If the
  answer is "nothing", that plugin is optional infrastructure, not
  a requirement.
- **Substrate-bound test.** Try to enumerate the requirement that
  bounds the substrate count at six (R-26, P-1). Is the bound
  arbitrary? What would 7 cost? What would 5 save?
- **Backup-contract design doc.** R-47 is a known gap. The
  follow-up dispatch is to write the missing requirement —
  per-substrate backup contracts (Neo4j, TimescaleDB, Mongo,
  Garage), tested restore drills, retention windows.

---

*End of §20.*
