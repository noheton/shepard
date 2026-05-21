# Shepard FAIR Compliance Evaluation
**Role:** Lead Research Data Manager (RDM) & FAIR Data Steward
**Date:** 2026-05-21
**Scope:** /opt/shepard codebase — backend entities, migration scripts, seed examples, design docs, and published integrations

---

## 1. What I Found (Actual Current State)

### Entity-level metadata fields

**Collection.java** (`de.dlr.shepard.context.collection.entities`) extends `AbstractDataObject`. Fields that exist today:

- Inherited from `AbstractDataObject`: `description` (String), `status` (String), `attributes` (Map<String,String>, free-form key-value with `||` delimiter)
- Inherited from `BasicEntity`: `name`, `annotations` (List<SemanticAnnotation>)
- Inherited from `VersionableEntity` chain: `appId` (UUID v7), `createdAt`, `updatedAt`
- Own fields: `heroImageUrl` (nullable String, additive since V54), `permissions`, `fileContainer`
- **Missing:** `license` field, `embargo` field, `funder`/`grantId` field, `rights` field, creator ORCID at entity level

**DataObject.java** (`de.dlr.shepard.context.collection.entities`) adds: `collection`, predecessor/successor/parent/child graph edges, `labJournalEntries`. **No FAIR-specific metadata fields.**

**User.java** has an `orcid` field with ISO 7064 mod 11-2 checksum validation. ORCID exists — but only on the User entity, not denormalized onto Collection or DataObject. The creator link is inferred at feed-build time by traversing `createdBy` relationships, not stored as a stamped field on the entity itself.

### PID / Publication infrastructure

**Publication.java** (`de.dlr.shepard.publish.entities`) stores: `pid`, `mintedAt`, `minterId` ("local"/"epic"/"datacite"), `publishedBy`, `entityKind`, `entityAppId`, `versionNumber`, `digitalObjectMutability`. Append-only (KIP1f retire sets `digitalObjectMutability = "retired"`).

**Crucially missing from Publication:** `license`. The KIP design doc (`aidocs/integrations/66-hmc-kip-integration.md §3`) notes `policy` (the HMC field for license) as "from Collection if set; otherwise instance default from `shepard.publish.default-license` config key" — but `Collection.java` has no `license` field. This is the planned KIP1e addition, which has **not shipped**.

`PublishService.buildMetadata()` constructs: digitalObjectType, name, dateCreated, dateModified, rightsHolder. License is explicitly noted as null pending KIP1e.

### Semantic / ontology layer

`V49__Bootstrap_internal_semantic_repository.cypher` creates only the singleton `:SemanticRepository` registry node. The 10 pre-seeded ontologies (PROV-O, Dublin Core, schema.org, FOAF, QUDT, OM-2, W3C Time, GeoSPARQL, OBO Relation Ontology, NFDI4Ing metadata4ing) are loaded at runtime by `OntologySeedService.java` using SHA-256-pinned Turtle bundles. Annotations are free-form semantic triples keyed by ontology term IRI — the system does not enforce that required controlled-vocabulary predicates are present on a given entity type.

### Export / RO-Crate

`ExportService.java` generates RO-Crate ZIPs including permissions, annotations, versions, subscriptions, timeseries as CSV, file bytes, structured data as JSON, URI references, basic references, and lab journal entries. The RO-Crate `ro-crate-metadata.json` references creator ORCID (pulled from User profile at export time). Snapshot-pinned exports (V2d) allow reproducible retrieval of a point-in-time view.

### Feed / Unhide integration

The Unhide (HKG) feed (`aidocs/integrations/67-unhide-publish-plugin.md`) emits `schema:license`, `schema:creator` (with `@id` = ORCID URI), `m4i:hasProcessingStep`. The `schema:license` value in the feed is populated from... nowhere yet (the entity field doesn't exist). The integration is designed against a license field that KIP1e will add.

### Seed showcase (lumen-showcase/seed.py)

DataObject `attributes` dicts contain: `test_run_id`, `date`, `bench`, `propellant`, `target_thrust_kN`, `target_mixture_ratio`, `duration_s`, `test_engineer` (plain name string, NOT an ORCID IRI), `notes_brief`, `is_fired`. Anomaly DOs add: `severity`, `hypothesis`, `scope`, `closed_at`. Semantic annotations use LUMEN_NS and SHEX_NS IRIs for phases/outcomes.

**Confirmed absent from all seed attributes:** `license`, ORCID IRI, `funder`, `grant_id`, `embargo_until`, DataCite-format PID field.

### InvenioRDM and Databus federation

Both are **designed but not shipped.** `aidocs/integrations/72-invenio-publishing-plugin.md` specifies a metadata mapping table where `license` and `embargo` are submission-time parameters in the request body (not stored on the entity). `aidocs/integrations/77-databus-moss-federation.md` designs PROV-O + m4i Turtle generation from the Collection provenance graph.

---

## 2. FAIR Score

### F — Findable: **2.3 / 3**

| Sub-criterion | Score | Evidence |
|---|---|---|
| F1: Globally unique, persistent identifiers | **Full** | UUID v7 appId on every entity + KIP1a ePIC/DataCite/local PID minting; `/v2/publish` shipped |
| F2: Rich metadata described with the identifier | **Partial** | name, description, status, attributes (free-form), semantic annotations. Missing: license, funder, rights enumeration. The PID record itself lacks license. |
| F3: Metadata registered/indexed in searchable resource | **Partial** | Unhide HKG feed ships PROV-O + m4i; Databus MOSS federation is designed but not shipped. No DataCite-shape catalog export exists. |

**Summary:** PID infrastructure is solid. Metadata richness at the entity level is not — free-form `attributes` can carry any field an operator types, but there is no schema enforcement, no completeness gate, and the Unhide feed pulls a license value from a field that doesn't exist yet.

---

### A — Accessible: **1.4 / 3**

| Sub-criterion | Score | Evidence |
|---|---|---|
| A1: Retrievable by identifier using open protocol | **Full** | REST API over HTTPS; appId-keyed `/v2/` surface; PID resolver redirects to entity page |
| A1.1: Protocol is open, free, universally implementable | **Full** | HTTP/REST; OpenAPI spec published at `/shepard/doc/openapi/v2.json` |
| A1.2: Protocol allows authentication where necessary | **Partial** | RBAC (Keycloak/OIDC) enforced, but no structured access-rights enum (OPEN/EMBARGOED/RESTRICTED) on entity; no machine-readable embargo end-date |
| A2: Metadata accessible even when data no longer available | **Weak** | KIP1f `retire()` tombstones the PID, but the Publication record only stores `digitalObjectMutability = "retired"` — no FAIR-compliant tombstone metadata document at the PID target |

**Summary:** Protocol layer is sound. The absence of an access-rights enum and embargo field means a machine cannot determine access conditions without human intervention. Tombstone handling is minimal.

---

### I — Interoperable: **2.0 / 3**

| Sub-criterion | Score | Evidence |
|---|---|---|
| I1: Knowledge representation language | **Full** | 10 pre-seeded ontologies including metadata4ing, PROV-O, Dublin Core, schema.org, FOAF, QUDT; content-negotiated JSON-LD / Turtle via PROV1 |
| I2: Vocabularies follow FAIR principles | **Partial** | NFDI4Ing metadata4ing (itself FAIR-certified); QUDT for units; but annotation predicates are not required from controlled vocab — free-form attributes sit alongside ontology-linked annotations with no bridge |
| I3: References use qualified references | **Partial** | Predecessor/Successor edges carry provenance; `m4i:hasProcessingStep` links exist in Unhide feed; but entity-to-entity links in the wire shape are Neo4j internal IDs (long) on legacy surface or appIds on `/v2/` — no use of PIDs as the link target |

**Summary:** Ontology layer is the strongest FAIR dimension. The gap is the bridge between free-form `attributes` and semantic annotations — there is no mechanism to assert that `attributes["test_engineer"]` IS `m4i:Method` or that a value is a QUDT unit. The seed example illustrates this gap concretely.

---

### R — Reusable: **0.9 / 3**

| Sub-criterion | Score | Evidence |
|---|---|---|
| R1: Rich metadata with plurality of accurate/relevant attributes | **Partial** | Free-form attributes allow richness, but schema is not enforced and typical seed data is sparse |
| R1.1: Clear and accessible data usage license | **Weak** | No `license` field on Collection or DataObject. KIP1e is planned but not shipped. Default instance license config key exists but is not surfaced in entity wire shape or PID record |
| R1.2: Associated with detailed provenance | **Partial** | PROV-O activity capture (ProvenanceCaptureFilter) for all API mutations; RO-Crate export includes creator ORCID pulled at export time. BUT creator ORCID is not a stamped field on the entity — it requires a live User record |
| R1.3: Meets domain-relevant community standards | **Weak** | metadata4ing seeded but not required; no DMP integration; no ORCID IRI as attribute value in seed examples; no validation that m4i:ProcessingStep properties are populated |

**Summary:** Reusability is the weakest dimension. This is not a design failure — it is a sequencing gap. KIP1e (license field), U5-ORCID (creator stamp on entity), and a Metadata Completeness Score would close most of this within one sprint cycle.

---

### Overall: **F: 2.3 / A: 1.4 / I: 2.0 / R: 0.9 → composite ~1.7 / 3**

The platform has outstanding PID and provenance infrastructure but trades reusability for flexibility — free-form attributes are powerful but give funders and harvesters nothing machine-actionable for license or creator identity.

---

## 3. Gap Analysis Table

| FAIR Dim. | Current State | Gap | Layer Fix |
|---|---|---|---|
| **F2** — Metadata richness | Free-form `attributes` map; semantic annotations optional | No license, funder, grant ID, rights, creator ORCID on entity wire shape | Backend: add `license` (SPDX String), `fundingReferences` (List), `creatorOrcids` (List) to `AbstractDataObject` or `Collection`; surface in `/v2/` IO classes |
| **F3** — Indexed in searchable resource | Unhide HKG feed with PROV-O/m4i; no DataCite catalog export | No DOI-registry-compatible catalog push; Databus MOSS not shipped | Plugin: `shepard-plugin-publisher` (see §5); DataCite Metadata Schema 4.5 mapping |
| **A1.2** — Access rights machine-readable | RBAC enforced; no structured access enum on entity | No `accessRights` enum (OPEN/EMBARGOED/RESTRICTED); no `embargoEndDate` on entity or Publication | Backend: add `accessRights` enum + `embargoEndDate` (ISO-8601 String, nullable) to `AbstractDataObject`; enforce in ExportService and Unhide feed |
| **A2** — Tombstone metadata | KIP1f sets `digitalObjectMutability = "retired"` on Publication | No FAIR-compliant tombstone document at PID target; caller gets 404 or redirect to deleted entity | Backend: generate static tombstone JSON-LD at PID resolve time when `digitalObjectMutability = "retired"`; include: name, dateRetired, reason, successor PID if available |
| **I2** — Controlled vocab enforcement | 10 ontologies seeded; annotations free-form; attributes entirely free-form | No bridge between `attributes` map and ontology terms; no required-predicate enforcement per entity type | Backend/Plugin: `shepard-plugin-metadata-profiles` — define required annotation predicates per entity type (Collection, DataObject per `digitalObjectType`); validate on publish |
| **I3** — Qualified references use PIDs | Predecessor/Successor edges exist; linked entities addressed by appId | Link targets are appIds, not resolvable PIDs; harvesters cannot dereference without shepard instance | Backend: when a KIP PID exists for a linked entity, surface it as the canonical reference in RO-Crate and Unhide feed `schema:mentions`/`prov:used` |
| **R1.1** — Usage license | `shepard.publish.default-license` config key; no entity-level field | No `license` on `Collection.java`; KIP PID record has no license; Unhide feed `schema:license` has no source | Backend: KIP1e — add `license` (SPDX String, nullable) to `Collection`, `AbstractDataObject`, and `Publication`; cascade to Unhide feed and RO-Crate |
| **R1.2** — Provenance / creator stamp | ORCID on User; pulled at export/feed-build time | Creator ORCID not stamped on entity at creation time; if User account deleted, provenance breaks | Backend: stamp `createdByOrcid` (nullable String) on `AbstractDataObject` at creation time; preserve even if User is deleted |
| **R1.3** — Domain community standards | metadata4ing seeded; not required | No DMP integration; no completeness score; seed examples show plain-name `test_engineer`, not ORCID IRI | Feature: Metadata Completeness Score (see §4); seed update: replace `test_engineer` string with `test_engineer_orcid` IRI |

---

## 4. DMP Compliance Feature Spec — Metadata Completeness Score Widget

### Problem

Funding bodies (DFG, Horizon Europe, Clean Aviation JU) require a Data Management Plan (DMP) that asserts how data will be described, shared, and preserved. Today, a shepard Collection can be exported with no license, no ORCID, no funder reference, and no access rights — yet the RO-Crate will claim provenance via PROV-O and the Unhide feed will assert `schema:creator`. This creates a gap between what the DMP promises and what the data actually carries.

### Proposed Feature: `MetadataCompletenessScore`

**Location in code:** `de.dlr.shepard.publish.services.MetadataCompletenessService` (new), surfaced at `GET /v2/collections/{appId}/metadata-completeness` and as a widget on the Collection detail page.

**Score computation (0–100, displayed as percentage):**

| Check | Points | Field / Source |
|---|---|---|
| Name present and non-empty | 5 | `AbstractDataObject.name` |
| Description ≥ 50 chars | 10 | `AbstractDataObject.description` |
| At least one semantic annotation | 10 | `BasicEntity.annotations` |
| `license` field set (SPDX identifier) | 20 | KIP1e: `Collection.license` |
| `accessRights` enum set | 10 | New: `AbstractDataObject.accessRights` |
| Creator ORCID stamped | 15 | New: `AbstractDataObject.createdByOrcid` |
| At least one `funder` reference | 15 | New: `AbstractDataObject.fundingReferences` |
| PID minted (KIP1a) | 10 | `Publication` exists for this entity appId |
| `embargoEndDate` set when accessRights = EMBARGOED | 5 | Conditional |

**Wire shape (response):**
```json
{
  "score": 72,
  "level": "GOOD",
  "checks": [
    {"id": "license", "passed": false, "label": "Usage license (SPDX)", "points": 20},
    {"id": "orcid", "passed": true, "label": "Creator ORCID", "points": 15}
  ],
  "minimumForPublish": 60,
  "minimumForHKGFeed": 80
}
```

**Frontend widget:** a compact progress ring on the Collection sidebar with color coding (red <50, amber 50–79, green ≥80). Clicking expands a checklist with inline fix links ("Add license →", "Add ORCID to your profile →").

**Publish gate:** `PublishService.publish()` checks score ≥ `minimumForPublish` (configurable, default 60) before minting a PID. Below threshold, returns HTTP 422 with the completeness response body. Override with `force=true` for admin use.

**DMP template integration:** `GET /v2/collections/{appId}/dmp-snippet` returns a Markdown block pre-populated from entity fields that an operator can paste into their DFG or Horizon Europe DMP form.

---

## 5. Repository Export Plugin Design — `shepard-plugin-publisher`

### Rationale

The current export surface is RO-Crate ZIP (pull-based, user-initiated). Funding bodies increasingly require **push-based deposit** to a certified repository (Zenodo, DaRa, InvenioRDM instance, B2SHARE). This cannot be a core feature because repository targets have independent release cadences, authentication schemes, and metadata mapping requirements. It is a canonical plugin-first candidate (CLAUDE.md §"Always: think plugin-first").

### Plugin identity

```
module:     shepard-plugin-publisher
compose:    profile: publisher
admin CLI:  shepard-admin publisher {status, configure, push, list-submissions}
```

### Architecture

```
CollectionPublishRequest (body)
  ├── targetRepository: "zenodo" | "invenio" | "dara" | "b2share"
  ├── license: SPDX string (required if Collection.license null)
  ├── embargo: ISO-8601 date (optional)
  ├── accessRights: OPEN | EMBARGOED | RESTRICTED
  └── dryRun: boolean

PublisherPlugin SPI (in core):
  interface RepositoryAdapter {
    SubmissionResult submit(Collection, CollectionPublishRequest, ExportResult);
    SubmissionStatus poll(String submissionId);
    RepositoryMetadata describe();
  }

Built-in adapters (in plugin):
  ZenodoAdapter    → Zenodo REST API (OAuth2 token)
  InvenioAdapter   → InvenioRDM REST API (per aidocs/72 mapping)
  DaraAdapter      → da|ra 3.4 API (DataCite-shape, German social sciences)
  B2ShareAdapter   → EUDAT B2SHARE REST API
```

### Metadata mapping (Zenodo example)

| Zenodo field | Source |
|---|---|
| `metadata.title` | `Collection.name` |
| `metadata.description` | `Collection.description` |
| `metadata.license` | request body OR `Collection.license` |
| `metadata.creators[].orcid` | `Collection.createdByOrcid` + DataObject contributors |
| `metadata.grants[].id` | `Collection.fundingReferences[]` |
| `metadata.related_identifiers[].identifier` | KIP PID (if minted) |
| `metadata.embargo_date` | request body OR `Collection.embargoEndDate` |
| `files` | RO-Crate ZIP from ExportService |

### Async flow

1. `POST /v2/collections/{appId}/publish` → `202 Accepted` with `submissionId`
2. Plugin builds metadata, calls `ExportService.export()`, streams ZIP to repository API
3. Repository returns DOI / accession ID → stored as new `Publication` record with `minterId = "zenodo"` (or other)
4. `GET /v2/publish/submissions/{submissionId}` polls status
5. On completion, NTF1 notification sent to collection owner

### Admin config (`:PublisherConfig` singleton)

```cypher
MERGE (c:PublisherConfig {appId: randomUUID()})
ON CREATE SET
  c.defaultRepository = 'zenodo',
  c.zenodoApiKey = '',
  c.zenodoSandbox = true,
  c.invenioBaseUrl = '',
  c.invenioToken = ''
```

Exposed at `GET/PATCH /v2/admin/publisher/config`. Token fields write-only (never returned in GET).

---

## 6. IP vs. Openness Decision Matrix

### MFFD (Multi-Functional Fuselage Demonstrator) data layers

The MFFD is a Clean Aviation JU co-funded demonstrator. Data generated is subject to the project's consortium agreement, which typically distinguishes:

| Data layer | Sensitivity | Recommended access rights | License | Repository path |
|---|---|---|---|---|
| **Raw AFP layup sensor logs** (laser power, compaction force, temperature arrays at 1 kHz) | Low-medium: process parameters are standard; specific robot kinematics may be proprietary | EMBARGOED (24 months post-project) → OPEN | CC BY 4.0 after embargo | Zenodo via plugin |
| **NDT scan results** (ultrasonic C-scan, thermography) | Medium: defect maps reveal manufacturing quality; may be commercially sensitive | RESTRICTED during project → EMBARGOED → OPEN | CC BY 4.0 after embargo | InvenioRDM (Helmholtz instance) |
| **Structural simulation models** (FEM, laminate analysis) | High: input geometry, material cards, and correlation data may carry IP | RESTRICTED (consortium only) | All rights reserved until publication | B2SHARE (access-controlled) |
| **CAD geometry references** (AP242 STEP files) | Very High: fuselage geometry is proprietary to premium AEROTEC/Airbus | RESTRICTED (no public release) | Proprietary | Internal only; never push-published |
| **Metadata / process graph** (Collection + DataObject provenance) | Low: structure of the process chain without data values | OPEN | CC BY 4.0 | Unhide HKG feed + Databus |
| **Ontology annotations** (CHAMEO terms, m4i:ProcessingStep links) | Low | OPEN | CC BY 4.0 | Unhide HKG feed |

### PLUTO data layers

PLUTO (referenced in task brief as Welzmüller et al. eLib 215120) is a DLR publication demonstrator. Based on domain context:

| Data layer | Recommended approach |
|---|---|
| Raw measurement data (if non-proprietary) | OPEN via Zenodo DOI; cite in DMP |
| Analysis scripts / Jupyter notebooks | OPEN; CC BY 4.0 or MIT; push to Zenodo alongside data |
| Derived publications / reports | OPEN; DLR eLib DOI; link from shepard Collection via URI reference |
| Proprietary inputs (third-party software configs) | RESTRICTED; reference by DOI with access request workflow |

### Decision rule for operators

1. Check consortium agreement / grant agreement for data categories.
2. For each shepard Collection, set `accessRights` + `embargoEndDate` + `license` at creation time (the Metadata Completeness Score gate will enforce this before PID minting).
3. Use the IP vs. Openness matrix above as a starting point; override with legal review for ITAR/EAR controlled data.
4. DMP snippet (`GET /v2/collections/{appId}/dmp-snippet`) generates the "Data description" and "Access and reuse" sections of the DMP form automatically from these fields.

---

## 7. External Sources Referenced

1. **HMC Kernel Information Profile (KIP) v1.1**
   Helmholtz Metadata Collaboration, 2023.
   https://helmholtz-metadaten.de/en/fair-data/hmc-fair-requirements
   *Relevance:* defines the 8 mandatory fields for ePIC-minted PIDs; directly implemented in shepard's `PublishService.buildMetadata()`. The `policy` (license) field is the outstanding gap.

2. **NFDI4Ing metadata4ing Ontology v3.0**
   NFDI4Ing Working Group, 2024.
   https://nfdi4ing.de/metadata4ing/
   *Relevance:* one of the 10 pre-seeded ontologies in shepard; provides `m4i:ProcessingStep`, `m4i:Method`, `m4i:Tool`, `m4i:NumericalVariable`, `m4i:InvestigatedObject` — the core predicates for engineering process annotation.

3. **DataCite Metadata Schema 4.5** (released January 2024)
   DataCite, 2024. DOI: 10.14454/3w3z-sa82
   https://schema.datacite.org/meta/kernel-4.5/
   *Relevance:* the target schema for DOI minting via the DataCite adapter (KIP1a `minterId = "datacite"`). Mandatory fields: `Creators` (with ORCID NameIdentifier), `Title`, `Publisher`, `PublicationYear`, `ResourceType`. Recommended: `Rights` (SPDX license identifier), `FundingReference`.

4. **ePIC Handle Service — Helmholtz**
   ePIC Consortium / Helmholtz AAI, 2024.
   https://www.gwdg.de/application-services/persistent-identifier
   *Relevance:* the ePIC PID minter used by `PublishService` when `minterId = "epic"`. Resolves via `hdl.handle.net` to the shepard entity page.

5. **Horizon Europe Open Science requirements**
   European Commission, Programme Guide 2024.
   https://ec.europa.eu/info/funding-tenders/opportunities/docs/2021-2027/horizon/guidance/programme-guide_horizon_en.pdf
   *Relevance:* Article 17 mandates open access to research data under FAIR principles; requires DMP submitted at project start and updated at major milestones; mandates deposit in certified repository with persistent identifier.

6. **RO-Crate Specification v1.1**
   Soiland-Reyes et al., 2022. DOI: 10.3897/rio.8.e93937
   https://www.researchobject.org/ro-crate/specification/1.1/
   *Relevance:* the format used by shepard's `ExportService` for RO-Crate ZIP export. Creator ORCID in `ro-crate-metadata.json` follows the `Person` entity pattern specified here.

7. **Clean Aviation JU Work Programme 2024**
   Clean Aviation Joint Undertaking, 2024.
   https://www.clean-aviation.eu/
   *Relevance:* the MFFD demonstrator project is co-funded under Clean Aviation; subject to Horizon Europe RDM mandates including FAIR data deposit and DMP requirements.

8. **DFG Guidelines on the Handling of Research Data**
   Deutsche Forschungsgemeinschaft, 2022 (updated).
   https://www.dfg.de/en/research_funding/principles_dfg_funding/research_data/
   *Relevance:* DFG requires a research data statement in all grant applications (since 2020); the 17 DFG requirements for research data management are aligned with FAIR principles. DFG-funded projects at DLR Augsburg are subject to these requirements.

9. **PLUTO paper** (Welzmüller, Dannemann, Scharringhausen et al.)
   DLR eLib reference 215120.
   *Note:* Not directly retrievable in this session. Referenced in task brief as a DLR RDM publication demonstrator. Based on domain context, treated as evidence that DLR researchers are already publishing FAIR-adjacent datasets and seeking RDM tooling alignment.

10. **SciCat vs. Coscine vs. NOMAD — comparison context**
    *SciCat* (ESS/PSI): experiment-first, NeXus-optimized, strong DOI minting; weak graph provenance.
    *Coscine* (RWTH Aachen/NFDI): metadata profile enforcement, DMP integration; weak timeseries.
    *NOMAD* (FHI Berlin): computational materials science specific; schema-enforced metadata; no general payload.
    **Shepard's differentiator:** graph provenance (Predecessor/Successor across heterogeneous payload kinds) + plugin-extensible storage + timeseries native. FAIR gap is R1.1 (license) and R1.2 (creator stamp) — both closable in one sprint.

---

## 8. Opportunities

### Near-term (1–2 sprints, high leverage)

1. **Add `license` (SPDX String, nullable) to `AbstractDataObject`** — one additive Neo4j field, no migration needed, brings R1.1 from Weak to Full. Surface in `CollectionIO`, `DataObjectIO`, and KIP PID record. This unblocks the Unhide feed `schema:license` and the InvenioRDM push adapter.

2. **Stamp `createdByOrcid` on `AbstractDataObject` at creation time** — copy from `User.orcid` at the moment of entity creation; preserve even if User account is later deleted. Closes the "live User required for provenance" gap.

3. **Add `accessRights` enum + `embargoEndDate`** — three-value enum (OPEN/EMBARGOED/RESTRICTED); `embargoEndDate` nullable ISO-8601 string. Surface in `GET /v2/collections/{appId}` and enforce in `PublishService` (reject ePIC mint for RESTRICTED unless `force=true`).

4. **Metadata Completeness Score endpoint** — `GET /v2/collections/{appId}/metadata-completeness` with the scoring rubric in §4. Frontend widget on Collection sidebar. This is the DMP compliance signal that funding body reviewers will ask for.

5. **Update `lumen-showcase/seed.py`** — replace `test_engineer: "Dr. Sarah Chen"` with `test_engineer_orcid: "https://orcid.org/0000-0000-0000-0000"` and add `license: "CC-BY-4.0"`, `funder: "Clean Aviation JU"`, `grant_id: "HORIZON-JU-Clean-Aviation-2023-01"` to all DataObject attributes dicts. This makes the demo seed FAIR-complete and sets the precedent for real datasets.

### Medium-term (plugin sprint)

6. **`shepard-plugin-publisher` (see §5)** — Zenodo adapter first (largest target audience); InvenioRDM adapter second (Helmholtz preference). This enables Horizon Europe-compliant deposit from inside shepard.

7. **Metadata profile enforcement plugin** — define required annotation predicates per `digitalObjectType` (e.g., an AFP layup DataObject must have `m4i:ProcessingStep` + `m4i:Tool`). Validate on PID mint. This closes I2 gap.

8. **DMP snippet generator** — `GET /v2/collections/{appId}/dmp-snippet` returning Markdown pre-filled with all FAIR-relevant fields. Direct copy-paste into DFG / Horizon Europe DMP form.

### Long-term (architectural)

9. **Qualified references using PIDs** — when a linked Collection or DataObject has a KIP PID, surface the PID (not the appId) as the canonical link target in RO-Crate `relatedItem` and Unhide `schema:mentions`. This closes I3 and enables cross-instance graph traversal.

10. **FAIR certification readiness score** — align the Metadata Completeness Score rubric with F-UJI (automated FAIR assessment tool) so shepard's internal score correlates with external FAIR certification runs. This is a credibility signal for funding agency auditors.

---

## 9. Real-World Impact — Funding Body Mandate Enablement

| Capability | Status | Horizon Europe Art. 17 | Clean Aviation JU | DFG Research Data Requirements | HMC KIP |
|---|---|---|---|---|---|
| Persistent identifier (PID) minting | **Shipped** (KIP1a) | ✓ | ✓ | ✓ | ✓ |
| Provenance capture (PROV-O) | **Shipped** (PROV1) | ✓ | ✓ | ✓ | ✓ |
| Machine-readable format (RO-Crate) | **Shipped** (V2d) | ✓ | ✓ | ✓ | Partial |
| Creator ORCID in export | **Partial** (pulled at export time; not stamped) | Partial | Partial | Partial | ✓ |
| Usage license on entity | **Gap** (KIP1e not shipped) | ✗ | ✗ | ✗ | ✗ |
| Access rights enum / embargo | **Gap** (not shipped) | ✗ | ✗ | Partial | ✗ |
| Funder / grant reference | **Gap** (not shipped) | ✗ | ✗ | ✗ | ✗ |
| Push-deposit to certified repository | **Designed** (aidocs/72, not shipped) | ✗ | ✗ | ✗ | — |
| Metadata completeness gate before publish | **Not designed** | — | — | — | — |
| DMP snippet generation | **Not designed** | — | — | — | — |

**Bottom line:** Shepard today can satisfy the PID, provenance, and format requirements of all four funding mandates. It cannot satisfy the license, access rights, funder reference, or certified-repository-deposit requirements until KIP1e and the publisher plugin ship. A researcher submitting an EU Horizon project in Q3 2026 who intends to use shepard as their DMP-compliant repository would need a workaround (manual Zenodo submission) for those three gaps.

---

## 10. What Surprised Me

**1. The ORCID field exists on User — but nowhere else.**
Shepard went to the effort of implementing ISO 7064 mod 11-2 ORCID checksum validation on the User entity. That is non-trivial work. Yet the ORCID is never stamped onto the Collection or DataObject at creation time. If a researcher's account is deleted, their contribution to a published dataset becomes anonymous. The fix is a one-line `entity.setCreatedByOrcid(user.getOrcid())` in the creation service — but it hasn't happened. This is the highest-leverage FAIR gap in the codebase.

**2. The Unhide feed asserts `schema:license` with no source entity field.**
The feed design document (`aidocs/67`) specifies that the feed will emit `schema:license`. But `Collection.java` has no `license` field. The feed builder will emit either a null or the instance-default config key. Harvesters querying the HKG feed will receive a license assertion that is not actually stored on the entity — it's a global fallback. This is architecturally inconsistent: the feed is more expressive than the entity model that backs it.

**3. The `attributes` map is both the system's greatest strength and its FAIR Achilles heel.**
Free-form key-value attributes are why shepard can accommodate any domain — rocket engine test rigs, AFP layup runs, structural simulations — without schema migrations. But FAIR requires machine-actionable metadata, and a key like `"test_engineer": "Dr. Sarah Chen"` is no more machine-actionable than a comment in a PDF. The bridge — requiring that certain `attributes` keys correspond to ontology-declared predicates, or replacing them with typed structured fields — is the architectural question shepard has not yet answered.

**4. The Predecessor/Successor provenance graph is FAIR-ready infrastructure that nobody is advertising.**
`m4i:hasProcessingStep` links in the Unhide feed, PROV-O activity capture, snapshot-pinned exports, RO-Crate with qualified provenance — this is sophisticated FAIR infrastructure that most RDM platforms don't have. Yet the README, seed examples, and user-facing docs barely mention it. The MFFD use case (AFP layup → ultrasonic weld → NDT → structural test) is a perfect demonstrator of exactly this capability, and it is currently not showcased. This is a positioning failure, not a technical one.

**5. KIP PID minting is idempotent by design but the tombstone is not FAIR-compliant.**
`KIP1f retire()` sets `digitalObjectMutability = "retired"` on the Publication record. This is correct per KIP convention. But when a harvester dereferences the PID after retirement, they should receive a tombstone document at the PID target — a machine-readable JSON-LD response that includes the name, retirement date, reason, and successor PID if available. Without this, the ePIC resolver will either 404 or redirect to a deleted-entity page. FAIR principle A2 ("metadata remain accessible even when data is no longer available") is violated by the current tombstone implementation.
