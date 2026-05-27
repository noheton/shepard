---
stage: deployed
last-stage-change: 2026-05-27
---

# 00 — Model inventory (SSOT)

**Status.** **Live.** Snapshot of "what the model looks like right now"
across every persistence substrate Shepard uses. Updated alongside
every PR that adds an entity, a migration, a new payload kind, or a
new persistence substrate. The companion change-ledger is
[`aidocs/34-upstream-upgrade-path.md`](../34-upstream-upgrade-path.md);
this doc is the snapshot, `34` is the diff stream.

**Audience.** Anyone designing against the Shepard data model;
operator triage; backlog grooming; the database optimisation pass
(`DB-OPT` in `aidocs/16`).

---

## §1 — Persistence substrates

Six stores. Every Shepard write lands in exactly one (and exactly one)
of these. The substrate split is **per-payload-kind** — not
per-collection, not per-tenant.

| Substrate | Role | Hot-path entity examples |
|---|---|---|
| **Neo4j** | Metadata graph + permission edges + provenance | `:Collection`, `:DataObject`, `:Permissions`, `:Activity`, `:Publication`, `:UnhideConfig` |
| **Postgres** | Relational app state | `app_user`, `subscription`, `importer_run`, `legacy_v1_config` |
| **TimescaleDB** (Postgres extension) | High-rate timeseries payloads | `timeseries` row per channel + per-timestamp value table |
| **MongoDB** | Structured-data payloads + GridFS (legacy file storage) | `structured_data_<oid>` collections; `fs.files` / `fs.chunks` for GridFS |
| **PostGIS** (Postgres extension, co-located on TimescaleDB since SPATIAL-V6-001) | Spatial-data payloads | legacy `spatial_data_points` table (GeoJSON-shaped rows) + new `shepard_spatial.profile` hypertable (engineering-process profiles: AFP brush traces, NDT scan paths, robot joint trajectories) |
| **Garage S3** | File-payload object storage (default) | `shepard-files` bucket, key `FileContainer<oid>/<file-oid>` |

Connection wait + readiness probing per substrate is governed by
`A1*` series rows in `aidocs/16`; recovery scheduler at `A1f`.

---

## §2 — Identity model

**Single rule:** every persistent entity carries a `appId` (UUID v7,
time-ordered, generated at write time by `AppIdGenerator`). UUIDv7
gives chronological ordering + cursor pagination + reproducible
distributed assignment without coordination.

- **Neo4j:** every `:HasAppId`-mixed-in node has a unique constraint
  on `appId`. Internal Long IDs (`id()`) still exist but are not
  surfaced on the `/v2/` wire. The legacy `/shepard/api/...` surface
  continues to emit Long IDs for upstream byte-compatibility.
- **Postgres / TimescaleDB / Mongo / PostGIS:** each row carries
  either an `appId` UUID column (new tables, e.g. `importer_run`) or
  is keyed by the OGM `Long id` until the L2 chain completes
  (`aidocs/platform/25-neo4j-id-migration-design.md`).
- **Garage S3 keys** are deterministic from Neo4j entity appIds:
  `<bucket>/FileContainer<container-appId>/<file-oid>`.
- **Future rename** `appId` → `shepardId` is gated on the SHACL
  substrate split + ID-MIG sequencing (`aidocs/16 §ID-MIG`).
  Don't rename ad-hoc.

---

## §3 — Neo4j label inventory

Snapshot as of 2026-05-23.

### Core entities (in `de.dlr.shepard.context.*.entities`)

`:Collection` · `:DataObject` · `:Permissions` · `:Roles` ·
`:User` · `:Role` (with `:HAS_ROLE` edges) · `:LabJournal` ·
`:LabJournalEntry` · `:Reference` (abstract — concrete sub-types
below) · `:SemanticAnnotation` · `:SemanticRepository`

### Reference families (per payload kind)

`:FileReference` · `:TimeseriesReference` ·
`:StructuredDataReference` · `:SpatialDataReference` ·
`:CollectionReference` · `:DataObjectReference` · `:UriReference`.
Container counterparts: `:FileContainer` · `:TimeseriesContainer` ·
`:StructuredDataContainer` · `:SpatialDataContainer`.

### Provenance + audit

`:Activity` (every backend mutation; PROV-O typed; carries HMAC chain
`auditHmac`/`auditPrevHmac`/`secretVersion` per SHACL-1) ·
`:Publication` (KIP records; one per published Collection / DataObject) ·
`:Watch` (subscription) · `:Notification` (NTF1, designed).

### Plugin-extended labels

`:UnhideConfig` (UH1a singleton) · `:SemanticConfig` (N1c2 singleton) ·
`:FeatureToggleRegistry` (A3b singleton) · `:LegacyV1Config`
(V1COMPAT.0 singleton) · `:InstanceConfig` (SHACL-1 singleton) ·
`:ShepardFile` (FS1a — files now carry `providerId` attribute pointing
at the active `FileStorage` adapter) · `:Snapshot` (forging chain) ·
`:Vocabulary` (SEMA-V6-002 — vocabulary registry; 10 bootstrap rows
seeded by V72; keyed on `uri`) · `:Predicate` (SEMA-V6-002 — typed
predicate registry; linked to `:Vocabulary` via `vocabularyAppId`).

### Relationships (selected)

`:HAS_PERMISSION`, `:HAS_REFERENCE`, `:HAS_PAYLOAD`, `:HAS_FILE`,
`:HAS_SEMANTIC_ANNOTATION`, `:PARENT_OF`, `:CHILD_OF`,
`:PREDECESSOR_OF`, `:SUCCESSOR_OF`, `:HAS_VERSION`, `:HAS_ROLE`,
`:HAS_ACTIVITY`, `:WATCHES`.

---

## §4 — Postgres + TimescaleDB schema inventory

Flyway-managed under `backend/src/main/resources/db/migration/V*.sql`
+ plugin migrations under `plugins/<id>/src/main/resources/db/migration/`.

| Table | Owner | Notes |
|---|---|---|
| `app_user` | core | OIDC subject mapping; the `Activity` writer joins against this |
| `subscription` | core | per-user collection-watch list |
| `timeseries` | core (Timescale hypertable) | one row per channel keyed by 5-tuple + `shepard_id` UUID (TS-ID PR-1 shipped per `aidocs/platform/87-timeseries-appid-migration.md`) |
| `spatial_data_points` | `shepard-plugin-spatiotemporal` (V1 schema, legacy) | one row per geo-tagged data point (GeoJSON-shaped; `V1.0.0__setup_spatial_data_tables.sql`) |
| `shepard_spatial.profile_container` | `shepard-plugin-spatiotemporal` (SPATIAL-V6-001) | one row per engineering-process container; metadata + `appId` UUID |
| `shepard_spatial.profile` | `shepard-plugin-spatiotemporal` (SPATIAL-V6-001) | TimescaleDB hypertable; one row per profile sample (container_id, time ns, position GEOMETRY, orientation GEOMETRY, measurements JSONB, annotations JSONB); BRIN + GIST + GIN indexes; 7-day compression policy; 4 space-partitions by `container_id` |
| `importer_run` | `shepard-plugin-importer` (IMP1b) | JobService-shaped run tracker |
| `legacy_v1_config` | `shepard-plugin-v1-compat` (V1COMPAT.0) | singleton mirror of the Neo4j `:LegacyV1Config` |
| `file_object_migrations` | core (FS1e1) | migration sweep tracker |

Conventions: `created_at` / `updated_at` columns on every mutable
table; `appId UUID UNIQUE` for entities with cross-store identity;
soft-delete via `deleted_at` (no hard deletes on user-facing tables).

---

## §5 — Mongo collection inventory

Stable collection names (per `de.dlr.shepard.data.structureddata.daos`):

| Collection | Notes |
|---|---|
| `fs.files` + `fs.chunks` | GridFS (legacy default; non-default after FS1b) |
| `structuredData_<containerOid>` | Per-container structured-data documents |

GridFS stays first-class supported after FS1a/b — not a deprecation
path. Operators choose: keep on GridFS (zero op cost) OR flip
`shepard.storage.provider=s3` for the S3 adapter.

---

## §6 — Garage S3 layout

Single bucket `shepard-files` (configurable). Key scheme:
`FileContainer<container-appId>/<file-oid>`. Migration tooling
(`shepard-admin files migrate`, FS1e) re-keys GridFS files into this
shape on the operator's command.

Per `aidocs/45 §3a` the backend speaks AWS SigV4 against any
S3-compatible endpoint (AWS S3, R2, B2, Wasabi, Garage, SeaweedFS,
Ceph RGW, MinIO).

---

## §7 — Wire-shape ground truth

The upstream OpenAPI 5.4.0 spec is in-tree at
`backend/src/test/resources/fixtures/v5/openapi-5.4.0.json` (282 KB,
**90 paths, 151 endpoints, 89 schemas, 24 tags**) — committed
2026-05-23 via commit `d7c035fe`. This is the **authoritative wire
contract** for everything under `/shepard/api/...`; `V1WireFidelityIT`
gates byte-for-byte fidelity; the v15.2 importer's
`compare_against_openapi` cross-checks live responses against it.

The `/v2/...` surface is fork-additive (per CLAUDE.md API-version
policy) and has its own OpenAPI emitted from the running backend at
`/shepard/doc/openapi/v2.json`.

---

## §8 — Recent changes (2026-05 session)

Changes that touched the persistence model or its supporting
infrastructure in this development window. The full diff stream lives
in [`aidocs/34-upstream-upgrade-path.md`](../34-upstream-upgrade-path.md);
this section is the inventory snapshot's pointer.

| Date | Commit | Inventory impact |
|---|---|---|
| 2026-05-22 | `fba3692c` | **PM1f sidecars()** declared on `PluginManifest` SPI — plugins now declare their infra deps (Garage, Kafka, …) so deploy assembles the compose from active-plugin declarations. No new entity; SPI-level. |
| 2026-05-22 | `a61c200f` | **TPL2a** — `PROCESS_RECIPE` + `VIEW_RECIPE` TemplateKinds added; new template families on `:Template`. |
| 2026-05-22 | `3d7e16fb` | **PROV-V15.1** — importer-only; per-DO F(AI)²R triples added as `:SemanticAnnotation` writes (not new labels — uses existing predicate slot). |
| 2026-05-22 | `5ad3dca7` | **PROV-V15.2** — importer-only smart warmup; no inventory delta. |
| 2026-05-22 | `997b5227` | Garage healthcheck CLI swap — infra only; no inventory delta. |
| 2026-05-22 | `327c7d08` | mongo image pin 8.0 → 8.0.4 — substrate version only; no schema change. |
| 2026-05-23 | `d7c035fe` | **V5SPEC** — upstream OpenAPI 5.4.0 spec committed in-tree as test fixture (not a model change; a contract-of-record change). |
| 2026-05-23 | `1cd2514a` | **DOC-STAGE** — meta only; all aidocs/*.md gain `stage:` front-matter; CLAUDE.md gains the tagging rule. No persistence-layer change. |
| 2026-05-23 | `fadf6dfd` | **M4I** — design only (`aidocs/semantics/94`); no inventory delta yet. Slices `M4I-a` through `M4I-f` queued. |
| 2026-05-23 | `26bd7292` | **Bibliography** — Pages-only, no inventory delta. |
| 2026-05-23 | `6cffbaef` | **GH-INFRA1** — `.github/*` only, no inventory delta. |
| 2026-05-23 | `f34bbf8b` | **ORIGIN-MYTH** — Pages-only, no inventory delta. |
| 2026-05-23 | (this commit) | **OBS-MFFD1** — `scripts/mffd-import-stats-collector.py` + systemd unit pair + `docs/help/observing-an-import.md`. **No schema change**: writes to existing `TimeseriesContainer` 590324 + `DataObject` 590344 inside the live MFFD-Dropbox collection. Recursive self-observability: Shepard's own ingest counters live in Shepard's own TS substrate. |
| 2026-05-24 | (this commit, LIC1) | **LIC1** — completes FAIR-1. `:AbstractDataObject` `license: String` (SPDX expression) + `accessRights: String` (enum stored as String: `OPEN \| RESTRICTED \| CLOSED \| EMBARGOED`) were already declared in V57 (NOOP, schema-free Neo4j properties). LIC1 adds the wire-level enum hint on the OpenAPI `@Schema`, 14 Jackson serialisation tests pinning NON_NULL omission + round-trip on Collection + DataObject IOs, and the frontend chip / input components + FAIR strip on both detail pages. **No new label, no new constraint, no new migration.** Both fields stay `@JsonInclude(NON_NULL)`-omitted on the wire when unset (verified by the v5 fixture corpus). |
| 2026-05-26 | (this commit, TPL4) | **TPL4** — `:SemanticAnnotation` gains a new optional schema-free property `source: String` (nullable; null = normal hand-authored annotation; `"attributes-backfill"` = synthetic node created by the TPL4 dual-write path). New `V69__TPL4_attributes_to_annotations_backfill.cypher` Cypher migration runs one-shot backfill for all pre-existing `DataObject` (and `Collection`) nodes with attribute properties. No new node label. No new relationship type. No new Neo4j index or constraint. The `source` field is additive and schema-free — existing `:SemanticAnnotation` nodes without it read as `null` (= normal annotation). `hashCode`/`equals` updated to include `source`. Two new `Constants` entries: `ANNOTATION_SOURCE_ATTRIBUTES_BACKFILL` + `TPL4_ATTRIBUTE_PREDICATE_PREFIX`. |
| 2026-05-26 | (this commit, TPL3a-lite) | **TPL3a-lite** — **New Neo4j label `:OntologyAlignment`**. Carries: `appId` (UUID), `shepardConcept` (String), `upperOntologyUri` (IRI String), `relationshipType` (rdfs:subClassOf \| owl:equivalentClass), `confidence` (HIGH\|MEDIUM\|LOW), `source` (aidocs path), `createdAt` (Long epoch-ms). New composite node-key constraint `OntologyAlignment_concept_uri_unique` (shepardConcept, upperOntologyUri) added by `V70__TPL3_upper_ontology_alignment.cypher`. 12 rows seeded at first startup (BFO/IAO/PROV-O/IOF Core alignment). New Java entity `OntologyAlignment` + DAO `OntologyAlignmentDAO` + response record `OntologyAlignmentIO`. New REST endpoint `GET /v2/semantic/ontology/alignment` (`@RolesAllowed("instance-admin")`). No new relationship types. No new Postgres/Timescale/Mongo/Garage changes. |
| 2026-05-26 | `dc43bfdc` | **SEMA-V6-001/002/003** — (001) `:SemanticAnnotation` gains 8 new nullable properties (`subjectKind`, `subjectAppId`, `vocabularyId`, `sourceMode`, `sourceActivityAppId`, `validFromMillis`, `validUntilMillis`, `confidence`). V71 adds two Neo4j performance indexes (`SemanticAnnotation_subjectAppId_idx`, `SemanticAnnotation_vocabularyId_idx`). No new label. (002) **Two new Neo4j labels**: `:Vocabulary` (fields: `appId`, `uri`, `label`, `prefix`, `description`, `enabled`, `createdAt`) + `:Predicate` (fields: `appId`, `uri`, `label`, `vocabularyAppId`, `expectedObjectType`, `cardinality`, `required`). V72 creates 4 uniqueness constraints (`.appId` + `.uri` for each) + seeds 10 bootstrap `:Vocabulary` rows keyed on `uri` (idempotent MERGE). (003) `:SemanticConfig` gains 4 new properties (`defaultVocabularyAppId`, `annotationMode`, `suggestionEnabled`, `suggestionModelId`). V73 backfills defaults via `coalesce` (idempotent). New admin endpoint `GET`+`PATCH /v2/admin/semantic/config`. 30 new tests across 4 classes. |
| 2026-05-27 | (this commit, PROMPT-h2) | **PROMPT-h2** — `:Collection` gains a new nullable property `promptLogMode: String` (valid values: `"HASH_ONLY"` / `"BODY_REDACTED"` / `"BODY_RAW"`; null treated as `"HASH_ONLY"`). Validated by the new `PromptLogMode` Java enum; stored as String in Neo4j to avoid OGM enum-serialisation friction. `V91__Set_default_promptLogMode_on_collections.cypher` backfills all existing `:Collection` nodes with `"HASH_ONLY"`. `CollectionIO` exposes the field with `@JsonInclude(NON_NULL)` — absent from v1 wire when null; present on v2 surface always after V91. Rollback: `V91_R__Rollback_Set_default_promptLogMode_on_collections.cypher`. No new Neo4j label. No new relationship type. No new index or constraint. |

**Honest summary of this session's inventory impact:** **zero new
Neo4j labels, zero new Postgres tables, zero new Mongo collections,
zero new Cypher migrations, zero new Flyway migrations.** The
session shipped (a) the v5 OpenAPI as the contract-of-record,
(b) the import script v15.1 + v15.2 (script-only), (c) the
documentation lifecycle taxonomy + retro-tag pass (meta-only),
(d) Pages-side bibliography + origin myth (meta-only), and
(e) `.github/*` scaffolding (meta-only). The model is unchanged.

The next inventory-delta-bearing rows are queued in `aidocs/16`:
**M4I-b** (predicate rename, no schema delta), **MFG5**
(DataObject status enum, requires Java enum + value-set check),
**ID-MIG1** (dual-emit `appId` + `shepardId` on `/v2/`), **DOC-STAGE2**
(CI gate on `stage:` front-matter), **DB-OPT2**
(hot-path-query indices on real MFFD data once ingest lands).

---

## §9 — How to use this doc

- **Designing a new payload kind?** Read §1–§3 first; pick the
  substrate; mint the labels per §2 identity rule; add a row to
  §3 / §4 / §5 in the same PR that ships the migration.
- **Designing a new endpoint?** Check §7 — if it's documented in the
  5.4.0 OpenAPI, the fork must serve byte-identical; new endpoints
  land under `/v2/` only.
- **Operating in production + something looks off?** Cross-reference
  §1 substrate-list with `:DbHealthState` + per-store
  `:RequiresDatabase` filter (A1c) to localise.
- **Triaging "where is the data for X stored?"** §3–§6 by label /
  table / collection / bucket-key prefix.

---

## §10 — Cross-references

- [`aidocs/34-upstream-upgrade-path.md`](../34-upstream-upgrade-path.md)
  — the diff stream (admin-facing change ledger)
- [`aidocs/44-fork-vs-upstream-feature-matrix.md`](../44-fork-vs-upstream-feature-matrix.md)
  — the per-feature progress tracker
- [`aidocs/platform/25-neo4j-id-migration-design.md`](../platform/25-neo4j-id-migration-design.md)
  — the L2 chain (Neo4j ID migration design)
- [`aidocs/platform/87-timeseries-appid-migration.md`](../platform/87-timeseries-appid-migration.md)
  — the TS-ID variant
- [`aidocs/platform/91-appid-uri-scheme.md`](../platform/91-appid-uri-scheme.md)
  — the URI scheme for `appId`
- [`aidocs/data/45-gridfs-to-s3-evaluation.md`](45-gridfs-to-s3-evaluation.md)
  — the file-payload substrate evaluation
- [`backend/src/test/resources/fixtures/v5/openapi-5.4.0.json`](../../backend/src/test/resources/fixtures/v5/openapi-5.4.0.json)
  — the upstream wire-contract source

**Maintenance rule:** every PR that adds an entity, a migration, a
new payload kind, or a new persistence substrate also updates §3 /
§4 / §5 / §6 / §8 here in the **same PR**. The diff stream
(`aidocs/34`) and the snapshot (this doc) move together.
