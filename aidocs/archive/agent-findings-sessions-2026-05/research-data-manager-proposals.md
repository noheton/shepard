# Proposal 1: KIP1e — License Field on All Entities (Core, foundational)

**Problem it solves.**
`Collection.java` and `AbstractDataObject` have no `license` field. The Unhide feed (`plugins/unhide`) already emits `schema:license` — currently sourced from a global instance-default config key, not from the entity. The KIP PID record (`plugins/kip`) has a `policy` field whose source is documented in `aidocs/integrations/66-hmc-kip-integration.md §3` but is unresolvable because the entity field doesn't exist. This is the single highest-leverage FAIR gap: it blocks R1.1 (usage license), blocks the Unhide feed from making per-entity license assertions, and blocks the publisher plugin (Proposal 6) from submitting meaningful metadata to Zenodo or InvenioRDM.

**What it looks like.**
Backend: add `license` (SPDX-expression String, nullable) to `AbstractDataObject`. Surface in `CollectionIO`, `DataObjectIO`, and all `/v2/` read responses. Add to `PATCH /v2/collections/{appId}` merge-patch surface. Cascade:
- `plugins/kip`: `PublishService.buildMetadata()` populates `policy` from `entity.getLicense() ?? shepard.publish.default-license`.
- `plugins/unhide`: `UhFeedBuilder` populates `schema:license` from `entity.getLicense()` instead of instance config.
- `ExportService`: `ro-crate-metadata.json` emits `"license"` on the root `Dataset` entity.

Frontend: a `v-select` (SPDX identifiers with common presets — CC-BY-4.0, CC0-1.0, MIT, All Rights Reserved) in the Collection edit dialog and the DataObject PATCH surface.

**Plugin or core?** Core. This is a field on `AbstractDataObject`, which every plugin compiles against. It belongs in the entity model, not in a plugin.

**Effort:** S (additive Neo4j field, no migration, propagation to three IO classes + two plugins + frontend select).

**Domain impact:** MFFD (IP layer classification: which AFP layers can be published under CC-BY vs. restricted), PLUTO (Horizon Europe Art. 17 mandate), general researcher (DFG data statement requirement), EU Horizon compliance, Clean Aviation JU DMP compliance, HMC KIP mandatory `policy` field.

**Cross-finding hook.** Unblocks Strategy-Advisor §8 Recommendation 3 (HMC Project Call 2026, deadline 6 July 2026 — `schema:license` in the HKG feed must be entity-specific, not instance-global). Addresses Manufacturing-Quality §2 row "7.6 Control of monitoring and measuring equipment" (IP-sensitive calibration certificates need license classification). Unblocks Proposal 6 (publisher plugin requires this as a hard dependency before any adapter can build its metadata mapping table). Referenced in Data-Ontologist §3.2 as a gap in the LUMEN seed: `attributes["propellant"] = "LOX/LCH4"` carries no license assertion; the 15-run campaign has no machine-readable reuse terms.

---

# Proposal 2: Creator ORCID Stamp on Entities at Creation Time (Core)

**Problem it solves.**
`User.java` has an ORCID field with ISO 7064 mod 11-2 validation — non-trivial work that went into the User entity but was never propagated to the entities the user creates. The ORCID is pulled at export time by traversing the `createdBy` relationship to the live User record. If the User account is deleted, the contribution becomes anonymous. DataCite Metadata Schema 4.5 (mandatory field: `Creators` with `NameIdentifier` = ORCID URI) cannot be satisfied from a live-User traversal alone. My own findings (§2, R1.2) show this as the highest-leverage FAIR gap in the codebase that requires only one line of new code: `entity.setCreatedByOrcid(user.getOrcid())` in the creation service.

**What it looks like.**
Backend: add `createdByOrcid` (nullable String, ORCID IRI format `https://orcid.org/…`) to `AbstractDataObject`. Populated in `DataObjectService.create()` and `CollectionService.create()` from the authenticated user's `User.orcid` field at the moment of creation. Nullable — if the user has not set an ORCID, field is null, no error. Cascade: `ExportService` uses this stamped field instead of live-User traversal; `PublishService` uses it to populate DataCite `Creators[0].nameIdentifiers[0]`; Unhide feed uses `schema:creator @id = entity.createdByOrcid`.

Frontend: no new UI required. The `ProfilePane.vue` ORCID edit (U1a, already shipped) is the entry point. Add a banner to the profile page: "Set your ORCID so it is stamped on all entities you create — required for DataCite DOI metadata."

**Plugin or core?** Core. This is a field on the universal entity model, not a plugin concern.

**Effort:** S (one field, one set-at-creation call, propagation to three consumers — ExportService, PublishService, Unhide feed builder).

**Domain impact:** PLUTO (FAIR R1.2 creator provenance, CCSDS and DFG mandate that datasets be attributable to named researchers), general researcher (all DFG-funded outputs require creator attribution), EU Horizon compliance.

**Cross-finding hook.** Addresses Data-Ontologist §3.2 (replace `test_engineer: "Dr. Sarah Chen"` freetext with a machine-readable ORCID link — the semantic annotation approach maps naturally once the ORCID IRI is on the entity). Addresses Manufacturing-Quality §2 row "8.5.2 Identification and traceability" (EN 9100 requires personnel traceability — a stamped ORCID is the machine-readable operator record). Complements Strategy-Advisor §3 Clean Aviation alignment: `Creators` in the DataCite DOI record is a mandatory field for Horizon Europe compliance.

---

# Proposal 3: Access Rights Enum and Embargo Date Field (Core)

**Problem it solves.**
No machine-readable access classification exists on any entity. A harvester or OIDC policy engine cannot determine whether a Collection is open, embargoed, or restricted without human intervention. FAIR principle A1.2 requires that the protocol allows authentication where necessary — which presupposes the system can signal whether authentication *is* necessary. The Unhide feed cannot emit `dct:accessRights` with a controlled value because the entity has no such field. The publisher plugin (Proposal 6) cannot submit embargo dates to Zenodo's API because there is no source field.

**What it looks like.**
Backend: add two fields to `AbstractDataObject`:
- `accessRights` (enum: `OPEN` | `EMBARGOED` | `RESTRICTED`, default `OPEN`)
- `embargoEndDate` (nullable ISO-8601 String, enforced non-null when `accessRights = EMBARGOED`)

Surface in `/v2/` read responses, merge-patch PATCH surface, and `ExportService` (RO-Crate emits `"conditionsOfAccess"` per the RO-Crate 1.2 spec). Validation: if `accessRights = EMBARGOED` and `embargoEndDate` is null, PATCH returns 422. `PublishService` blocks ePIC mint for `RESTRICTED` entities unless `force=true` (admin override). Unhide feed emits `dct:accessRights <http://purl.org/coar/access_right/c_14cb>` (embargoed) or the corresponding COAR IRI.

Frontend: add `accessRights` v-select and `embargoEndDate` date-picker to the Collection edit dialog (alongside the license select from Proposal 1). Show an amber banner on the Collection detail page when `embargoEndDate` is in the past but `accessRights` is still `EMBARGOED`.

**Plugin or core?** Core. Universal entity property.

**Effort:** S–M (two additive fields, validation rule, four consumer cascade points, frontend edit controls).

**Domain impact:** MFFD (IP layer matrix from my findings §6: NDT scans embargoed 24 months post-project, structural simulations restricted during consortium period), PLUTO (mission data access policies), general researcher (DFG requirement: data access conditions must be documented), EU Horizon compliance.

**Cross-finding hook.** Addresses Manufacturing-Quality §2 "EASA Part 21 G retention" gap (RESTRICTED entities with explicit end-dates provide the legal access control that EASA auditors need). Addresses UX-Auditor finding that the Collection sidebar has no status/access summary for a curator — this field, surfaced as a chip in the sidebar, gives curators at-a-glance access classification without opening the edit dialog.

---

# Proposal 4: Funder References Field (Core)

**Problem it solves.**
No funder or grant reference field exists on any entity. Horizon Europe Art. 17, DFG Guidelines requirement 12, and DataCite Metadata Schema 4.5 `FundingReferences` block all require machine-readable grant attribution on published datasets. Today, the LUMEN seed embeds `grant_id` as a freetext attribute key — which is not queryable as a typed field, not emitted by the Unhide feed, and not mapped to any DataCite field by `PublishService`. My FAIR score analysis gives R1.3 (meets domain-relevant community standards) a "Weak" rating specifically because of this gap.

**What it looks like.**
Backend: add `fundingReferences` (List<FundingReference>, stored as `@Properties`-serialized JSON array) to `AbstractDataObject`. `FundingReference` is a value type: `{funderName: String, funderRor: String (nullable), grantId: String, grantTitle: String (nullable)}`. Serialize as `fundingReferences||0||funderName = "Clean Aviation JU"` etc. using the existing `||`-delimiter pattern from `attributes`. Cascade: `PublishService` maps to DataCite `FundingReferences[]`; Unhide feed emits `schema:funding` with `schema:funder` and `schema:identifier`; RO-Crate emits `"funder"` on the root Dataset entity.

Frontend: a repeatable form field in the Collection edit dialog ("Add funder"), with an ROR autocomplete field for `funderRor` (call `https://api.ror.org/organizations?query=` — public, no auth required) and a text field for `grantId`. Common presets: "Clean Aviation JU", "European Commission (Horizon Europe)", "DFG".

**Plugin or core?** Core. Universal FAIR metadata property.

**Effort:** M (new value type with serialization, ROR autocomplete in frontend, cascade to three publisher consumers).

**Domain impact:** MFFD (Clean Aviation JU co-funded — grant attribution is a contractual deliverable), PLUTO (DFG or ESA funded — funder attribution required in DMP), general researcher, EU Horizon compliance, DFG compliance.

**Cross-finding hook.** Addresses Strategy-Advisor §4 ROI model: "DMP / EN 9100 audit data retrieval" time savings rely on having funder attribution in machine-readable form so the DMP snippet (Proposal 5) can auto-fill it. Referenced in Analytics-AI §5 ("training data curation: which datasets are publishable fine-tuning material?") — a dataset with `accessRights = OPEN` and a `fundingReferences` entry for a public-domain grant is a candidate; one without is not.

---

# Proposal 5: Metadata Completeness Score + DMP Snippet Generator (Core)

**Problem it solves.**
Shepard can today export a Collection with no license, no ORCID, no funder reference, and no access rights — yet the RO-Crate will assert PROV-O provenance and the Unhide feed will emit `schema:creator`. There is no feedback loop that tells a researcher "your data is not publishable yet" before they click "Mint DOI". This is the structural gap between what the DMP promises and what the data actually carries. My findings (§4) specify the exact scoring rubric; this proposal ships it.

**What it looks like.**
Backend: new `MetadataCompletenessService` (under `de.dlr.shepard.publish.services`) computing a 0–100 score from nine weighted checks:

| Check | Points |
|---|---|
| Name present and non-empty | 5 |
| Description ≥ 50 chars | 10 |
| At least one semantic annotation | 10 |
| `license` field set (SPDX) — Proposal 1 | 20 |
| `accessRights` enum set — Proposal 3 | 10 |
| `createdByOrcid` stamped — Proposal 2 | 15 |
| At least one `fundingReferences` entry — Proposal 4 | 15 |
| PID minted (KIP1a `Publication` exists) | 10 |
| `embargoEndDate` set when accessRights = EMBARGOED | 5 |

New endpoint: `GET /v2/collections/{appId}/metadata-completeness` returning `{score, level (POOR/FAIR/GOOD), checks[], minimumForPublish, minimumForHKGFeed}`. Gate: `PublishService.publish()` checks score ≥ `minimumForPublish` (admin-configurable via `:PublisherConfig`, default 60) before minting PID. Returns HTTP 422 with the completeness body if below threshold; `?force=true` for admin override.

Second endpoint: `GET /v2/collections/{appId}/dmp-snippet` returning a Markdown block pre-populated from entity fields (name, description, license, funder, ORCID, PID if minted, accessRights, embargoEndDate). Text is in DFG/Horizon Europe DMP section style — copy-paste into the DMP form. An Analytics-AI Proposal 3 (STRUCTURED LLM) extension can draft the prose around the fields, but the snippet is valuable without LLM involvement.

Frontend: a compact progress ring on the Collection sidebar (red <50, amber 50–79, green ≥80). Clicking expands a checklist with inline fix links: "Add license →" (scrolls to edit dialog), "Add ORCID to your profile →" (navigates to `/me`). The ring replaces or sits alongside the existing status chip in the sidebar header.

**Plugin or core?** Core. The score is computed over universal entity fields; it is not domain-specific.

**Effort:** M (new service, two endpoints, sidebar widget with reactive score, fix-link navigation).

**Domain impact:** General researcher (every DFG/Horizon Europe proposal needs a FAIR-ready dataset), EU Horizon compliance, DFG compliance, Clean Aviation JU.

**Cross-finding hook.** Directly addresses UX-Auditor Gap G4 (sidebar is currently navigation-only; this gives it a data-quality signal that curators act on). Addresses Manufacturing-Quality §2 "9.1.1 Monitoring" (the quality score field on TimeseriesReference — AI1c — is never surfaced in the UI; the completeness score widget establishes the precedent for surfacing quality signals in the sidebar). The DMP snippet generator connects to Analytics-AI §3 STRUCTURED capability: once `shepard-plugin-ai` ships, the snippet endpoint can optionally call the LLM to draft the "access and reuse" prose section around the structured fields.

---

# Proposal 6: `shepard-plugin-publisher` — Repository Push-Deposit (Plugin, Zenodo-first)

**Hard dependency: Proposals 1, 3, and 4 must ship first.** The publisher plugin's metadata mapping table requires `license`, `accessRights`, `embargoEndDate`, and `fundingReferences` as source fields. Without them, the plugin submits incomplete metadata that will be rejected by Zenodo's API validation.

**Problem it solves.**
Current export is pull-based (RO-Crate ZIP, user-initiated download). Horizon Europe Art. 17, DFG Guidelines, and Clean Aviation JU require push-based deposit to a certified repository with persistent identifier. A researcher submitting an EU Horizon project in Q3 2026 who intends to use Shepard as their DMP-compliant repository must today perform manual Zenodo submission. This is a textbook plugin-first candidate: repository targets have independent release cadences, authentication schemes, and metadata mapping requirements — they cannot be a core feature without violating the plugin-first rule from CLAUDE.md.

**What it looks like.**
Plugin: `shepard-plugin-publisher` (compose profile: `publisher`). Defines a `RepositoryAdapter` SPI in core with three methods: `submit(Collection, CollectionPublishRequest, ExportResult)`, `poll(submissionId)`, `describe()`. Plugin ships with one built-in adapter: `ZenodoAdapter` (OAuth2 token, Zenodo REST API). InvenioRDM adapter deferred to v2 of the plugin (Helmholtz preference, see `aidocs/integrations/72-invenio-publishing-plugin.md`).

Async flow:
1. `POST /v2/collections/{appId}/publish-to-repository` → `202 Accepted` + `{submissionId}`
2. Plugin calls `ExportService.export()`, builds Zenodo metadata from entity fields, streams ZIP to Zenodo API
3. Zenodo returns DOI → stored as new `Publication` record with `minterId = "zenodo"`
4. `GET /v2/publish/submissions/{submissionId}` polls status
5. On completion, NTF1 notification to collection owner (NTF1 is a dependency — if not yet shipped, fall back to a Shepard in-app banner)

Admin config: `:PublisherConfig` Neo4j singleton seeded by migration. Fields: `defaultRepository`, `zenodoApiKey` (write-only), `zenodoSandbox` (default `true`). Exposed at `GET/PATCH /v2/admin/publisher/config`. CLI parity: `shepard-admin publisher {status, configure, push, list-submissions}`.

Zenodo metadata mapping:

| Zenodo field | Source |
|---|---|
| `metadata.title` | `Collection.name` |
| `metadata.description` | `Collection.description` |
| `metadata.license` | `Collection.license` (Proposal 1, required) |
| `metadata.creators[].orcid` | `Collection.createdByOrcid` (Proposal 2) |
| `metadata.grants[].id` | `Collection.fundingReferences[].grantId` (Proposal 4) |
| `metadata.embargo_date` | `Collection.embargoEndDate` (Proposal 3) |
| `metadata.access_right` | derived from `Collection.accessRights` (Proposal 3) |
| `files` | RO-Crate ZIP from `ExportService` |

**Plugin or core?** Plugin. External integration, own release cadence, own auth — explicit CLAUDE.md plugin-first case.

**Effort:** L (one adapter, async job pattern, admin config singleton, CLI parity, integration test with Zenodo sandbox).

**Domain impact:** MFFD (Clean Aviation JU data deliverable deposit), PLUTO (Horizon Europe open data mandate), general researcher (DFG certified-repository deposit requirement), EU Horizon compliance, DFG compliance.

**Cross-finding hook.** Strategy-Advisor §8 Recommendation 3 (HMC Project Call 2026) identifies the absence of push-deposit as the most cited gap in HMC project calls. Analytics-AI §5 "Training data curation" finding: once a Collection has a Zenodo DOI, its publishability status is machine-readable — a future training-data curation filter can query `Publication.minterId = "zenodo"` to identify externally deposited (and therefore IP-cleared) datasets.

---

# Proposal 7: FAIR-Compliant PID Tombstone (Core)

**Problem it solves.**
FAIR principle A2 ("metadata remain accessible even when data is no longer available") is violated by the current retirement implementation. `KIP1f retire()` sets `digitalObjectMutability = "retired"` on the `Publication` record. When a harvester dereferences the ePIC PID after retirement, the PID resolver either 404s or redirects to a deleted-entity page. There is no tombstone document. My FAIR scoring gives A2 a "Weak" rating for this reason.

**What it looks like.**
Backend: new `TombstoneService` that generates a static tombstone response when a retired entity's PID is dereferenced. When `GET /shepard/api/collections/{appId}` (or the `/v2/` path) is called for an entity whose `Publication.digitalObjectMutability = "retired"`, instead of 404, return HTTP 410 (Gone) with `Content-Type: application/ld+json` and a tombstone body:

```json
{
  "@context": "https://schema.org",
  "@type": "Dataset",
  "identifier": "<ePIC PID>",
  "name": "<entity name at time of retirement>",
  "datePublished": "<mintedAt>",
  "dateModified": "<retiredAt>",
  "description": "This dataset has been retired. <reason if available>",
  "isReplacedBy": "<successor PID if minted>",
  "license": "<license at time of retirement>"
}
```

The tombstone fields are sourced from the `Publication` record (which is immutable and persisted post-retirement). Add `retiredAt` (nullable timestamp) and `retiredReason` (nullable String) to `Publication`. Add `successorPid` (nullable String) to `Publication` — when a successor DataObject has been minted, the tombstone includes `isReplacedBy`.

Frontend: the Collection detail page, when `digitalObjectMutability = "retired"`, renders a full-width amber banner: "This collection has been retired. [View successor →]" (when `successorPid` is present). DataObject detail page: same pattern.

**Plugin or core?** Core. The PID and retirement are core infrastructure (KIP1f is in the `plugins/kip` plugin, but the tombstone response must be served by the core request handler — the entity endpoint exists in core).

**Effort:** M (tombstone response logic in entity service, new fields on Publication, frontend banner, integration with KIP plugin's `retire()` method to write `retiredAt` + `retiredReason`).

**Domain impact:** General researcher (any published dataset retirement), MFFD (process DataObjects that are superseded by later revisions need tombstones so cross-referencing systems don't silently 404), EU Horizon compliance.

**Cross-finding hook.** Addresses API-Scrutinizer finding on "Missing Operations" — the scrutinizer noted that callers navigating from a provenance trail to a retired entity get no useful response. The 410 + tombstone closes this gap cleanly. The `isReplacedBy` field in the tombstone is the machine-readable form of the successor PID reference, which the API-Scrutinizer also flagged (referencing retired entities via PIDs rather than appIds).

---

# Proposal 8: Qualified PID References in RO-Crate and Unhide Feed (Core)

**Problem it solves.**
FAIR principle I3 (references use qualified references) is partially satisfied: Predecessor/Successor edges exist in Neo4j, and linked entities are addressed by `appId`. But `appId` is not a globally resolvable identifier — a harvester from another system cannot dereference a Shepard `appId` without knowing the Shepard instance URL. When a DataObject has a KIP PID minted (ePIC or DataCite DOI), that PID *is* globally resolvable and should be used as the canonical link target in exports and feeds. My findings (§2, I3, "Partial") document this gap.

**What it looks like.**
Backend: in `ExportService`, when generating the `ro-crate-metadata.json` `relatedItem` entries for a DataObject's predecessors/successors, resolve each linked entity's `appId` against the `Publication` table. If a `Publication` record exists with `minterId != "local"` (i.e., an ePIC or DataCite DOI has been minted), emit the PID as the `identifier` in the `relatedItem` entity instead of the `/v2/` URL. If no PID exists, fall back to the `/v2/` URL (current behaviour — no regression).

In the Unhide feed builder, emit `schema:mentions` and `prov:used` using PID URIs (when available) instead of appId-based internal URLs.

New utility method: `PublicationService.findPidForEntity(String appId) -> Optional<String>` — one index lookup against `Publication.entityAppId`.

**Plugin or core?** Core. The RO-Crate export and Unhide feed are core infrastructure; only the PID resolution logic changes.

**Effort:** S–M (additive logic in ExportService and Unhide feed builder, one new utility method, no new endpoints).

**Domain impact:** PLUTO (cross-instance provenance — PLUTO data referencing LUMEN calibration data across two Shepard instances), general researcher (any federated discovery scenario), EU Horizon compliance (EOSC federated discovery requires globally resolvable identifiers in `relatedItem`).

**Cross-finding hook.** Addresses API-Scrutinizer "referenceIds Problem" from the broader ecosystem angle: the scrutinizer's fix (`timeseriesReferenceAppIds`, `fileReferenceAppIds`) addresses internal navigation; this proposal addresses external navigation — harvesters need PIDs, not appIds. Addresses Strategy-Advisor §3 Helmholtz KG alignment: the HKG harvester uses the Unhide feed; if `schema:mentions` contains PIDs, the KG can traverse cross-dataset provenance edges between any two HKG-registered datasets.

---

# Proposal 9: `plugins/unhide` — Improvement: Entity-Level License + Qualified PID Emission

**Problem it solves.**
This is the explicit plugin improvement required by the task. The Unhide plugin (`plugins/unhide`) currently emits `schema:license` from a global instance-default config key, not from the entity's `license` field (which doesn't exist until Proposal 1 ships). It emits `schema:mentions` using Shepard-internal appId-based URLs, not PIDs. Both gaps cause the HKG feed to be less FAIR than it claims. The HMC KIP validator run against the feed would flag both.

**What it looks like.**
Two changes to `plugins/unhide`, dependent on Proposals 1 and 8:

1. **Entity-level license in feed.** After Proposal 1 ships, update `UhFeedBuilder.buildCollectionEntry()` to source `schema:license` from `Collection.license` (if set) with fallback to `shepard.publish.default-license`. Add a feed-validation check: if `license` is null after fallback, omit the `schema:license` triple rather than emitting a null IRI (which would make the feed invalid JSON-LD).

2. **Qualified PID references in `schema:mentions`.** After Proposal 8 ships, update the feed builder's `schema:mentions` emission to use the `PublicationService.findPidForEntity()` utility — emit the PID URI when available, fall back to the `/v2/` URL. This makes the HKG feed traversable from outside the Shepard instance.

Additionally, add a feed self-validation endpoint: `GET /v2/admin/unhide/feed-validation` (instance-admin only) that runs the feed builder for the top-10 most recently modified Collections and reports which ones would fail HMC KIP mandatory-field checks. This is the "shift left" signal for operators.

**Plugin or core?** Plugin improvement — changes are entirely within `plugins/unhide`, except for the shared utility in Proposal 8 (which is core).

**Effort:** S (once Proposals 1 and 8 ship — these are the hard dependencies; without them, this proposal has nothing to wire up). The feed-validation endpoint is S independently.

**Domain impact:** General researcher (every Helmholtz-affiliated researcher who needs their data discoverable in the HKG), MFFD (the HKG is the HMC-mandated discovery endpoint for Clean Aviation JU data), Strategy-Advisor §8 Recommendation 3 (HMC Project Call 2026 deadline 6 July 2026 — a working HKG feed with correct entity-level license is a prerequisite for the call).

**Cross-finding hook.** Addresses Strategy-Advisor §1 ("Adoption numbers — unknown vs. 1,500+ for Coscine") by improving the quality of the HKG feed, which is the primary discovery path for Helmholtz-affiliated researchers who don't already know Shepard exists. Addresses Data-Ontologist §5 (Opportunity 3, SSN/SOSA and CHAMEO seeding) — once CHAMEO defect terms are in the n10s graph, the Unhide feed could emit `m4i:hasProcessingStep` with CHAMEO defect class values, which would make MFFD inspection data uniquely discoverable in the HKG by defect type.

---

# Proposal 10: Metadata Profile Enforcement Plugin (`shepard-plugin-metadata-profiles`)

**Problem it solves.**
Free-form `attributes` are Shepard's greatest strength (any domain can be accommodated without schema migrations) and its FAIR Achilles heel (a key like `test_engineer: "Dr. Sarah Chen"` is no more machine-actionable than a comment in a PDF). FAIR I2 requires that vocabularies follow FAIR principles — which means required predicates must be enforced, not just suggested. The Completeness Score (Proposal 5) tells a researcher what's missing; this plugin tells the system what is *required* for a given entity type before status can advance. This is the structural fix for the attributes–annotations duality gap documented in Data-Ontologist §1.1 and my own findings §2.

**What it looks like.**
Plugin: `shepard-plugin-metadata-profiles`. Defines a `MetadataProfile` entity (Neo4j, stored in the plugin's package) with fields: `name`, `appliesToDigitalObjectType` (String, e.g., `AFP_LayupRun`), `requiredAttributes` (List<String> — attribute keys that must be non-null), `requiredAnnotationPredicates` (List<String> — ontology property IRIs that must have at least one annotation), `minimumStatusForEnforcement` (enum — enforce only when status ≥ this value, e.g., enforce from `IN_REVIEW` onwards).

Admin surface: `GET/POST/PATCH/DELETE /v2/admin/metadata-profiles` (instance-admin). A UI pane in the admin panel shows defined profiles, their required fields, and a "test this profile against Collection X" dry-run.

Enforcement hook: in `DataObjectService.updateStatus()`, before advancing to `IN_REVIEW` (or the configured threshold), call `MetadataProfileService.validate(dataObject)`. If any required attribute or annotation is missing, return HTTP 422 with a structured list of missing fields. The researcher sees this as an inline error in the status dropdown.

Built-in profiles (seeded by the plugin):
- `LUMEN_TestRun`: requires `attributes[bench, propellant, date]` + annotation predicates `[m4i:Method, prov:wasAssociatedWith]`
- `MFFD_AFP_LayupRun`: requires `attributes[fiber_lot_id, part_number, ply_count]` + annotation predicates `[shex:ManufacturingProcess, m4i:Tool]`

**Plugin or core?** Plugin. Domain-specific profiles don't belong in core. The enforcement *hook* (the status-transition gate) must be in core as an SPI, but the profile definitions and their evaluation logic live in the plugin. This is the CLAUDE.md plugin-first rule applied to a cross-cutting capability that has domain-specific implementations.

**Effort:** L (plugin module with Neo4j entity, admin REST, status-transition hook SPI in core, enforcement logic, built-in profile seeding, admin UI pane).

**Domain impact:** MFFD (DIN EN 9100 §7.8.2 traceability — required fields for each process step), PLUTO (CCSDS metadata completeness for mission data), general researcher (Coscine's metadata profile enforcement is its primary NFDI4ING differentiator — this closes the gap), EU Horizon compliance.

**Cross-finding hook.** Addresses Data-Ontologist §5 Opportunity 1 (the data ontologist's highest-leverage recommendation is to connect the `attributes` map to ontology terms — this plugin operationalizes that connection as an enforcement rule, not just a suggestion). Addresses Manufacturing-Quality §3 NCR Design ("no required fields enforcement on the DataObject") — the profile plugin provides exactly the required-field gate that an NCR DataObject needs before its status can advance to `READY`. Bridges the gap that UX-Auditor identified with `AddAnnotationDialog` (50 dialogs for 50 channels) — a profile that requires `m4i:NumericalVariable` on every channel becomes the machine-enforced driver for channel annotation, complementing the UX-Auditor's Idea C (annotation suggestion from channel name).

---

# Proposal 11: Ancestor-Chain Lineage Walk Endpoint (Core)

**Problem it solves.**
Compliance Auditor persona (UX-Auditor §"Persona 3") identified this as a CRITICAL gap: the provenance graph is truncated at 6 predecessors (`slice(0, 6)` in `DataObjectProvGraph.vue`), and there is no UI or API for recursive ancestor traversal. A DIN EN 9100 auditor tracing a 4-hop defect chain (AFP ply → layup run → material batch → supplier certificate) currently must navigate DataObject by DataObject, opening the Provenance panel on each. Manufacturing-Quality §2 row "7.8.2 Traceability" is rated MAJOR specifically because "no directed lineage walk API exists yet (queued as `aidocs/30`)." This proposal ships the first milestone of `aidocs/30`.

**What it looks like.**
Backend: new endpoint `GET /v2/data-objects/{appId}/ancestor-chain?depth=10&direction=upstream` (also `downstream`, `both`). Returns an ordered list of DataObject summaries from the queried node back to the root (or to the requested depth), each with: `appId`, `name`, `status`, `digitalObjectType`, `hopDistance`. Implemented as a bounded-depth Cypher query:

```cypher
MATCH path = (root:DataObject)-[:HAS_SUCCESSOR*1..10]->(target:DataObject {appId: $appId})
WITH root, length(path) AS hops
ORDER BY hops DESC
RETURN root.appId, root.name, root.status, hops
LIMIT 50
```

This is a single graph traversal, not N sequential fetches. Gated by the standard `PermissionsService.isAllowed()` check on the starting entity.

Frontend: "Trace upstream" button in the DataObject Provenance panel (UX-Auditor Idea A). Opens a linear timeline view (not a force graph) showing the ancestor chain as a scrollable card list: DataObject → Predecessor → Predecessor's Predecessor. Each card shows name, status chip, hop distance, and a "navigate to" link. The 6-predecessor truncation in `DataObjectProvGraph.vue` is replaced by a "View full chain" link that opens this linear view.

**Plugin or core?** Core. Graph traversal over the core Predecessor/Successor relationship is a fundamental platform operation, not domain-specific.

**Effort:** M (Cypher query, one endpoint, bounded-depth service logic, permission gate, frontend linear-timeline view replacing the force-graph truncation).

**Domain impact:** MFFD (DIN EN 9100 §7.8.2 full traceability chain from finished panel to raw material batch), general researcher (any multi-step experimental chain), compliance auditors.

**Cross-finding hook.** Directly resolves UX-Auditor CRITICAL finding (6-predecessor truncation in `DataObjectProvGraph.vue` lines 86/106) and CRITICAL finding (no recursive predecessor walk UI). Addresses Manufacturing-Quality §2 row "8.7.3 Rework and repair records" — the ancestor chain walk is how an auditor confirms that the rework loop (AFP fail → NDT → rework → NDT pass → next step) is complete and traceable. Addresses Data-Ontologist §5 Opportunity 2 (material batch as first-class graph node) — once material batches are DataObjects with Predecessor links, the ancestor-chain endpoint makes the entire "CF/LMPAEK batch B3 → SkinPanel-001" chain traversable in one API call rather than a JSON-blob scan.

---

# Proposal 12: LUMEN Seed FAIR Upgrade (Seed Script, Not a Feature)

**Problem it solves.**
The LUMEN seed (`examples/lumen-showcase/seed.py`) is the primary showcase demonstrating Shepard's capabilities. It currently embeds `test_engineer: "Dr. Sarah Chen"` as a freetext attribute (not an ORCID IRI), has no `license` field, no `funder` reference, no `accessRights`, and stores propellant lot IDs (`lox_lot_id`, `lch4_lot_id`) in StructuredData JSON blobs rather than as DataObject attributes or Predecessor links. Every peer agent's findings cite this gap: Data-Ontologist §9 "attributes['propellant'] = 'LOX/LCH4' is repeated 15 times"; Strategy-Advisor §10 "the LUMEN showcase is a clever fiction that risks becoming a liability"; my own findings §2 "Confirmed absent from all seed attributes: license, ORCID IRI, funder, grant_id, embargo_until."

**What it looks like.**
Changes to `examples/lumen-showcase/seed.py` (no backend change required — all fields being set already exist as `attributes` keys; Proposals 1–4 add typed fields once they ship, and a follow-on seed update will use those):

1. Replace `"test_engineer": "T. Marek"` with `"test_engineer_orcid": "https://orcid.org/0000-0000-0000-TEST"` (placeholder ORCID IRI, clearly marked synthetic).

2. Add to every DataObject `attributes`:
   - `"license": "CC-BY-4.0"`
   - `"funder": "Clean Aviation JU"`
   - `"grant_id": "HORIZON-JU-Clean-Aviation-2023-01"` (synthetic)
   - `"access_rights": "OPEN"`

3. Promote `lox_lot_id` and `lch4_lot_id` from the StructuredData JSON blob to the DataObject `attributes` dict — making them Cypher-queryable without JSON scan. Also create two MaterialBatch DataObjects (`LOX-2024-01`, `CH4-2024-01`) as Predecessors of all LUMEN test run DataObjects, demonstrating the pattern documented in Data-Ontologist §5 Opportunity 2.

4. Add unit annotations to the 25 LUMEN timeseries channels using the `AnnotatableTimeseries` bridge — each channel gets a QUDT unit annotation (`qudt:Bar`, `qudt:Kelvin`, `qudt:G`, etc.) using the already-seeded QUDT ontology. This makes the LUMEN dataset the reference example for how to annotate channels, not just a showcase of raw data.

5. Add a `description` ≥ 50 chars to every DataObject (many currently have short descriptions) so the Completeness Score (Proposal 5) reads ≥ 80 for the showcase dataset out of the box.

**Plugin or core?** Seed script — not a backend or plugin concern.

**Effort:** S (seed script changes only; no backend code; the seed is already structured and the pattern is clear).

**Domain impact:** Demonstration value for every domain. Strategy-Advisor explicitly identified the seed as a liability if it doesn't reflect FAIR practices; this closes that gap.

**Cross-finding hook.** Addresses Strategy-Advisor §4 Risk 4 (single-showcase dependency — the LUMEN seed is the only fully deployed corpus; making it FAIR-complete transforms it from a "clever fiction" into a reference implementation researchers can copy). Addresses Analytics-AI §5 "curation needs" — after this update, the LUMEN dataset passes the `license`, `funder`, and `access_rights` checks that a training-data curation filter would apply, making it a legitimate candidate for publication as a fine-tuning dataset.
