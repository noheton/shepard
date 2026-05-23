---
title: Requirements alignment — DaMaST workshop × UX 5-phase journey × MFFD-focused gap analysis
stage: feature-defined
last-stage-change: 2026-05-23
audience: strategy, backlog-curator, MFFD-readiness, contributor
anonymity: strict — no individual names from the DaMaST workshop or questionnaire responses; institute-level codes (BT / DW / SC, plus the questionnaire-section codes AS-BS, AS-GÖ, AS-KP, RA-P8, RB-MRB, RY-SPB, RY-SRT) are the smallest acceptable attribution unit
---

# Requirements alignment — DaMaST workshop × UX 5-phase journey × MFFD-focused gap analysis

Maps the **DaMaST (Data Management for Space Transport) Requirement Workshop** topic set (DLR Göttingen, 2025-10-23/24, 93-page Protokoll) against the 2024 UX **5-phase user journey** (`aidocs/frontend/01-user-research-findings-2024.md`) and the SSOT backlog at `aidocs/16-dispatcher-backlog.md`; pulls out **MFFD-relevant** gaps as concrete additions. Anonymity mirrors `project_ux_research_2024_anonymity.md` — institute-level only, never individuals.

DaMaST is a sibling consortium to MFFD; the substrate decisions (Shepard backend + invenioRDM + Databus/MOSS per pp.13-16) overlap MFFD's substrate, so DaMaST gaps translate directly into MFFD-readiness gaps.

---

## §1 — Deliverable 1: Topic inventory from DaMaST v1

The Day-1 "Introduction to Data Governance" deck (pp. 4-11) enumerates **9 canonical Data-Governance topics** plus a set of cross-cutting questionnaire findings. Each row below is verbatim from the deck or the synthesised additions in the institute interviews (3.1 - 3.8 of the Protokoll, pp. 17-93).

| Topic name (verbatim) | One-line summary | Workshop session | Flagged by (institute-level) |
|---|---|---|---|
| **Data Policy Principles (FAIR)** | Findable / Accessible / Interoperable / Reusable as the governing frame; "FAIR is not open"; align with DLR Data Strategy + Helmholtz KPI for Software & Data | Day-1 deck intro, all Deep Dives | BT, DW, SC (all) |
| **Technical Support Needs** | Form-filling assistance (templates / chatbot / AI), metadata extraction from files (NLP / AI), validation tools, helpdesk + training | Day-1 deck intro | DW, SC; questionnaire converges from every institute |
| **Metadata Embargo Rules** | Delay between dataset creation and publication; embargo scope (individual vs default); reasons (IP / publication delay); minimum visible metadata even under embargo | Day-1 deck intro | DW, BT (likely link to elib publication embargo) |
| **Handling Sensitive Data** | Classification level, export-controlled content; dual-use awareness; contractual/legal restrictions; multi-channel timeseries that become sensitive only when joined (RY-SPB addition) | Day-1 deck + Deep Dive I | RY-SPB explicitly; RB-MRB on flight-data sensitivity; BT on industrial IP |
| **Derived Data** | Licensing inheritance, attribution / provenance tracking, publication & monetisation, dual-use character of derivatives | Day-1 deck intro | DW, BT |
| **Data Format Standardisation** | Common standards / framework across divisions; open & interoperable formats; ontologies & vocabularies (EOSC, RDA alignment); explicit lack of CF-Standard-Names equivalent in space-transport domain | Day-2 Deep Dive II | All institutes (every questionnaire: "None / Everyone uses their own list") |
| **Access Control & Permissions** | Owner / steward / consumer / external collaborator roles; manual vs semi-automated workflows; auditability; **three-tier metadata visibility** — general metadata vs specific metadata vs experimental data (Schindler synthesis p.58) | Day-1 Deep Dive I (primary topic) | BT, DW, SC; RB-MRB flags LDAP/comet group friction |
| **Data Management Planning (DMP)** | Define how data is created/stored/shared/preserved; dedicated resources & roles ("data steward" position); template alignment with institutional + funder requirements; Living DMPs; Actionable DMPs (DMP triggers automated processes) | Day-2 Deep Dive | DW, SC (BT noted "people overwhelmed → removed actionable DMPs near-term") |
| **Data Lifecycle & Storage** | End-of-life planning (retention/disposal); active (hot) vs archived (cold); transition policies; tooling (Tivoli StorageManager cited as anti-example) | Day-2 Deep Dive | All institutes (every questionnaire: "There is no strategy") |
| **Crawler & Metadata Extraction** (synthesised) | Inventory of existing data holdings; "base version provided centrally; per-institute extensions for custom formats"; first focus on data discovery: "what data is available?" (Schindler synthesis p.58) | Synthesised from RA, RB-MRB, RY interviews | RA, SC (synthesis) |
| **PIDs / DOIs / Persistent identifiers** | Local vs global identifiers; "DOI-like would be nice if data made available outside DLR" (AS-GÖ); local mission-IDs only at RB-MRB | Questionnaire convergence | All institutes: "None" today |
| **Provenance** | History/lineage of data; multiple institutes answered "I have no idea" or "None — sounds like we should consider it"; explicit need for "connection from design through testing, experiment and final analysis" (RY-SPB) | Questionnaire convergence | All institutes: minimal/none today |
| **Controlled Vocabularies / Ontologies** | Shared meaning + structure for metadata; CF Standard Names cited as exemplar from atmospheric science; explicit gap in space-transport | Day-2 Deep Dive II + questionnaire | All institutes: "There is none / Everyone uses their own list" |
| **FAIR self-assessment + Helmholtz KPI for Software & Data** | Self-assessment tooling for institutes/departments; strategy link to Helmholtz KPI; introduction of new tools beyond self-assessment | Day-1 deck (cross-cutting) | DW, SC strategy framing |
| **Calibration certificates + equipment metadata** | Equipment metadata documentation; calibration values + intervals; lab-book / DMA tools used today | Questionnaire convergence | RA (test bench), RB-MRB (sensor cubes), RY-SPB ("QR based system in transition"), AS-KP (calibration data) |
| **Versioning across binary formats** | Git fine for source; CAD / FEM / CFD binary files have no structured version-diff path; explicit interest from RY-SPB ("focus on identifying differences between CAD versions") | Questionnaire convergence | RY-SPB, RY-SRT, RA |

This 16-row list is the spine of Deliverable 2.

---

## §2 — Deliverable 2: Alignment matrix

Status legend: **shipped** | **🚧 in-flight** | **📐 designed** | **queued** | **none** (no backlog row).
MFFD-relevance: **HIGH** = directly needed for live MFFD ingest / AFP-welding chain. **MEDIUM** = relevant but not blocking. **LOW** = DaMaST-relevant but not MFFD-specific.

| DaMaST topic | UX-journey phase(s) | aidocs/16 task (with ID) | Status | MFFD-relevance | Gap? |
|---|---|---|---|---|---|
| Data Policy Principles (FAIR) | Discovery, Working-with-data | FAIR1 (license), FAIR2 (createdByOrcid), FAIR3 (accessRights enum + embargo), FAIR4 (completeness score), FAIR5 (seed FAIR-completeness), FAIR8 (F-UJI alignment) | FAIR1-5 **shipped**; FAIR8 queued | **HIGH** | **NO** (covered) |
| Technical Support Needs — form-filling + extraction | Create-structure, Import | T1 templates **shipped** (T1a–T1f); shepard-plugin-ai AI1a **shipped**; AI1n form-filling assist; AI1f metadata-extraction from text; #126 importer plugin **queued** | T1 shipped; AI1 family partial | **HIGH** | **PARTIAL** — see §3.1 |
| Metadata Embargo Rules | Working-with-data | FAIR3 `accessRights ∈ {OPEN, EMBARGOED, RESTRICTED}` + `embargoEndDate` (nullable ISO-8601) on `AbstractDataObject` | **shipped** | MEDIUM | NO (covered) |
| Handling Sensitive Data — classification + dual-use | Working-with-data | FAIR3 covers OPEN/EMBARGOED/RESTRICTED; **no SENSITIVE / EXPORT_CONTROLLED tier**; no dual-use marker; no "join-makes-sensitive" combinatorial guard | **partial** | **HIGH** (MFFD = industrial IP) | **YES** — see §3.2 |
| Derived Data — license inheritance + attribution | Working-with-data | FAIR1 (license field) + PROV1 family (Activity, predecessor lineage) + UH1c (RO-Crate-like attribution); **no automatic license-inheritance rule across `prov:wasDerivedFrom`** | **partial** | MEDIUM | **YES (minor)** — see §3.7 watch list |
| Data Format Standardisation | Create-structure, Import | N1 family (ontology preseed: 14 bundles incl. m4i, QUDT, PROV-O, NASA Thesaurus, shepard-experiment), TERM1/2/3 (glossary from wiki), IOT1 (SOSA/SSN bundles); M4I-a..f deepening **designed** | partial; M4I-b is a CRITICAL fix | **HIGH** | **PARTIAL** — see §3.5 |
| Access Control & Permissions — basic roles | Working-with-data | A0 instance-admin **shipped**; PermissionsService UR/UW/MR/MW model **shipped from upstream**; F4 cache invalidation **shipped**; F5 fail-closed guard **shipped** | shipped | **HIGH** | NO (covered for the 4-role case) |
| Access Control — **three-tier metadata visibility** (general / specific / experimental) | Discovery, Working-with-data | **No row** — the upstream UR/UW/MR/MW pair gives 2 tiers (metadata vs data), not 3 | **none** | **HIGH** | **YES** — see §3.3 |
| DMP — basic snippet | Discovery, Working-with-data | FAIR7 — `GET /v2/collections/{appId}/dmp-snippet` returns Markdown pre-filled | **queued** | MEDIUM | NO (queued) |
| DMP — Living DMP + Actionable DMP (auto-trigger) | Working-with-data | **No row** — FAIR7 is a one-shot snippet; no notion of DMP as a live document that drives downstream actions | **none** | LOW (MFFD has informal DMP) | YES — watch list |
| Data Lifecycle & Storage — retention | Working-with-data | SM1 (storage management design) — see `project_storage_management.md`; FS1h per-Collection storage choice **parked** | **designed, not built** | MEDIUM | **PARTIAL** — see §3.6 |
| Data Lifecycle — hot/cold archive transitions | Working-with-data | **No row** — Garage is a single warm tier; no archival/cold-tier policy | **none** | LOW (MFFD lifecycle is years not decades) | YES — watch list |
| Crawler & Metadata Extraction — inventory of existing holdings | Discovery, Import | #126 shepard-plugin-importer (library of importers — built-in + git-referenced); MFFD ingest itself = v15 importer; AI1f metadata-extraction-from-text | **partial** (ingest yes; inventory crawl no) | **HIGH** (Wave-3 cells per `aidocs/strategy/100`) | **YES** — see §3.4 |
| PIDs / DOIs | Working-with-data | `appId` (UUID v7) is the local PID; UH1 publishes to Helmholtz Unhide which mints KIP record PIDs; **no DOI minting in-fork** (deferred to invenioRDM integration per BT/DW sync) | partial | MEDIUM | NO (acceptable — invenioRDM owns it) |
| Provenance | Working-with-data | PROV1a-h family **shipped** (`:Activity` + automatic capture + JSON-LD/m4i content-neg + dashboard + retention TTL); cross-instance prov UI **designed** (`aidocs/frontend/100`); PROV-V15.1 import provenance **shipped** | mostly shipped | **HIGH** | NO (well-covered) |
| Controlled Vocabularies / Ontologies | Create-structure, Working-with-data | N1a-l **shipped + queued** (n10s + 14 bundles); IOT1 SOSA/SSN; M4I-c m4i DataObjectShape; AI1q SPARQL tool for LumenKG | mostly shipped | **HIGH** | NO (covered — see also §3.5 for the M4I-b bug fix flagged separately) |
| FAIR self-assessment + Helmholtz KPI alignment | Discovery | FAIR4 (`MetadataCompletenessScore` endpoint) **shipped**; FAIR8 (align rubric with F-UJI) **queued**; **no Helmholtz KPI export** | partial | LOW | **YES (minor)** — see watch list |
| Calibration certificates + equipment metadata | Create-structure, Working-with-data | **No row** — calibration is currently a free-text annotation; no first-class `CalibrationCertificate` shape; no equipment-as-individual ontology | **none** | **HIGH** (MFFD AFP head + IQZ NDT cells, per `aidocs/strategy/100` Layer-4) | **YES** — see §3.5 |
| Versioning across binary formats | Working-with-data | PV1a/PV1b versioning **shipped** (snapshot mechanism + version field); **no binary-diff** for CAD/CFD | partial | MEDIUM | NO for ingest; YES (minor) for diff — watch list |

Sixteen DaMaST topics → six confirmed Gap=YES rows + two PARTIAL rows. Detailed in §3.

---

## §3 — Deliverable 3: MFFD-focused gap list + recommendations

The HIGH-MFFD-relevance Gap=YES items, with proposed backlog rows.

### §3.1 Form-filling assistance + AI-extraction at import time (PARTIAL gap)

**What the user needs.** DaMaST flagged this as **Long-Term / High-Impact / Low-Effort**. Convergent questionnaire signal: metadata "is available somewhere but not structured" (AS-BS) or "minimal — tool, user, date, project" (RY-SPB). MFFD specifically: ZLP AFP runs produce metadata in `.tdms` headers, `.netcdf` parfiles, AFP-robot OPC UA captures — all currently lost or re-typed at import.

**What's there.** AI1a `LlmProvider` SPI shipped; AI1f (metadata-extraction-from-text) **designed** not shipped; T1 templates ship `AttributeSpec` but no auto-fill from the file being uploaded.

**Proposed.** Extend AI1f + sibling:

- **AI1f (queued):** widen to typed file payloads (`.tdms` headers, NetCDF global attrs, HDF5 attrs, EXIF, PDF first-page). Backend hook: `FileImportInterceptor` runs after dimension extraction, before persist; populates `AttributeSpec` slots with confidence; 1-click review dialog.
- **AI1f-PARFILE (new, MFFD-anchor):** TAU `parfile` (NetCDF aux header per AS-BS) + `b2000++pro` MAPDL parameter-file readers. Size: S (parsers exist as Python in the institutes).

**Effort sizing.** AI1f as-designed is M. Adding the parfile reader is S. Together: M.

**Blocks.** Doesn't block live MFFD ingest (v15 importer continues to populate attributes from `_meta.json`); does **block the next-wave MFFD ingest pattern** where ZLP staff drop raw files into a watched folder and Shepard self-populates attributes.

---

### §3.2 Sensitive-data tier on `AbstractDataObject` (Gap=YES)

**What the user needs.** Three asks converge: BT industrial IP on MFFD (AFP recipe IS DLR IP); RY-SPB synthesis p.69 — "flight data might be security-related … individually the timeseries are not critical but, when joined, allow conclusions on sensitive characteristics" → **combinatorial dual-use**; RB-MRB on export-control transport.

The current FAIR3 `accessRights` enum (`OPEN / EMBARGOED / RESTRICTED`) collapses "internally-shared", "industrial IP", and "dual-use export-controlled" into one bucket — operationally insufficient.

**Proposed row.**

- **FAIR9 (new, MFFD-anchor):** Extend `AbstractDataObject.accessRights` enum with `SENSITIVE` (industrial-IP, internal restricted) and `EXPORT_CONTROLLED` (dual-use marker, triggers audit pre-publish). Add `sensitivityRationale: String` (free text — why it's sensitive). Make the upgrade UI-visible: a sensitivity badge on the DataObject card, a Collection-level summary showing the worst-tier child. Size: S (additive field + enum value + UI badge) + S (audit-trail integration via PROV1a) = M.
- **FAIR9-COMBO (new, watch-list-ready):** "Combinatorial sensitivity" tag on a SemanticAnnotation predicate: `shepard:sensitiveWhenJoinedWith` linking two-or-more TimeseriesReferences whose combination becomes sensitive even when the individual references aren't. Surfaces as a permission check on multi-channel export queries. Size: M–L; design first.

**Adjacent existing rows to extend.** FAIR3 is the parent; the row above is a sibling. PROV1a captures the audit trail of accessRights changes automatically.

**Blocks.** Blocks **publishing MFFD data externally** under the current rollout plan (Wave 4 *Panel Produktion* demo per `aidocs/strategy/100`). MFFD ingest itself is unblocked (everything imports as RESTRICTED for now), but the cell-by-cell rollout to TPZ/IQZ + the eventual cross-domain Wave-5 federation can't proceed without this.

---

### §3.3 Three-tier metadata visibility — general / specific / experimental (Gap=YES)

**What the user needs.** DaMaST Schindler synthesis (p.58 + p.69): "access control probably needs at least three levels: general metadata (title, contact), specific metadata (experiment settings), experimental data". Today Shepard's permission model gives **two** tiers: metadata vs data (UR/UW gates the metadata read/write; MR/MW gates the underlying timeseries/file payload).

A MFFD-specific case: AFP run TR-004 should be discoverable by a colleague in TEZ ("there was an AFP run on date X") — general-metadata visibility, but not the propellant batch numbers / consolidation force traces — specific-metadata visibility — let alone the raw thermal-trace timeseries.

**Proposed row.**

- **PERM-3TIER (new, design-first):** Add a third permission band `GENERAL_METADATA` to the Permissions tuple. `AbstractDataObjectIO` projection respects band: GENERAL_METADATA tier sees only `{appId, name, createdAt, ownerOrcid, accessRights, count-of-children}`; specific-metadata adds all `attributes` + `annotations`; data tier adds reference payloads. Backend: extend `PermissionsService.Roles(generalRead, metadataRead, dataRead, …)`; add `GENERAL_METADATA_READ` AccessType; F4 cache key absorbs the new tier without signature growth. Frontend: low-tier viewers get a stub card with a "request access" CTA. Size: M (backend additive + cache + IO projection) + S (frontend stub card) = M–L.
- **DOC.** Update `aidocs/24-permission-system-review.md` with the third-band proposal; new ADR.

**Adjacent existing rows.** A0 (instance-admin) is orthogonal; C5/C5b are upstream cleanup work — neither extends the band model. PR-1d (permission audit trail) already lives on PROV1a + `/v2/admin/permission-audit`.

**Blocks.** Doesn't block the live MFFD ingest. **Blocks Wave-1 cell rollout** to TPZ/IQZ because cross-cell discovery ("what AFP-similar work exists on TPZ?") is the moment researchers will demand general-metadata visibility while keeping specific-metadata + data restricted.

---

### §3.4 Crawler / inventory of existing holdings (Gap=YES — only ingest covered today)

**What the user needs.** Schindler synthesis (p.58): "first focus on data discovery: 'what data is available?' To cover existing data, it would be nice to provide a crawler — base version provided within the project (folder traversal, example metadata extraction, pushing to data catalog) while specific extensions (extraction from custom formats) would be up to the individual institutes." This is the DaMaST DataHub vision (Databus + MOSS pipeline, BT/DW sync p.15-16). Every questionnaire response: institutes have 1-100 TB on NFS / SAN / HPC scratch with no index.

ZLP-specific (Wave-3 cells per `aidocs/strategy/100`): Heißpresse, Öfen, Wasserstrahlanlage all have years of `.tdms` / `.csv` / proprietary outputs on local file shares; the wave plan assumes shepard-plugin-importer #126 covers it — but #126 is currently scoped to one-shot conversion, not periodic crawl-and-index.

**Proposed row.**

- **CRAWL1 (new, plugin-shaped):** `shepard-plugin-crawler` skeleton. Periodic walk over a configured root path (NFS / SMB share / S3 bucket); per-file kind detection (mime + extension + magic-byte chain); declarative recogniser → `AttributeSpec` extraction (reuses AI1f / §3.1); produces a **Discovery Collection** of stub DataObjects whose attributes carry `source_path`, `discovered_at`, `crawler_pass_id` provenance. Operator decides whether to promote a stub to a full ingest (which then triggers the regular v15-style import). Three deploy modes: `--periodic` (cron / @Scheduled), `--once` (one-shot), `--diff` (sync-compare against previous pass). Output flows into Databus per BT/DW sync (Schindler p.16). Size: L (new plugin + admin runtime config + Discovery-Collection visualiser).
- **CRAWL1-MFFD (new, sub-row):** ZLP-NFS-share crawler config preset — knows about ZLP's typical folder layout (`{cell}/{date}/{run-id}/`), the `_meta.yaml` convention if it exists, and the TDMS+CSV+OPCUA-log file kinds expected per cell. Size: S after CRAWL1 lands.

**Adjacent existing rows.** #126 (importer-plugin library) is complementary not a substitute — #126 takes a file/folder and produces full DataObjects on demand; CRAWL1 does the **discovery** that tells the operator what's worth importing in the first place. AI1f / §3.1 supplies the recogniser layer.

**Blocks.** Doesn't block live MFFD ingest (operators continue to do explicit v15.x imports). **Blocks Wave 3 of the rollout plan** (Heißpresse / Öfen / Wasserstrahl cells where the operator doesn't know what's already on the NFS share); blocks the DaMaST DataHub integration story end-to-end.

---

### §3.5 Calibration certificates + equipment metadata as first-class artefacts (Gap=YES)

**What the user needs.** Convergent questionnaire signal: every experimentally-instrumented institute calls out calibration as missing or as ad-hoc. RA test bench (P8) flags ISO-9001-compliant calibration as a regulation. RY-SPB ("transitioning to a QR-based system so that data about a component can be accessed via portable devices"). RB-MRB: "calibration values" cited per stage. MFFD: the AFP-head sensors (thermal camera, laser-line, consolidation roller) have manufacturer calibration intervals; the IQZ NDT cell's laser-US + air-US + thermography all carry per-instrument calibration; none of this is currently linkable into a DataObject's audit trail.

This is also a **DIN EN 9100 audit gap** per `aidocs/agent-findings/manufacturing-quality.md §2`.

**Proposed row.** This is best implemented as a **shape** (a SHACL template) plus a small backend extension, not a brand-new container kind:

- **CALIB1 (new):** SHACL template `:CalibrationCertificate` per `aidocs/semantics/95` Part 2. Required attributes: `instrumentSerialNumber`, `calibrationDate`, `validUntil`, `calibrationStandard` (controlled vocab), `accreditingBody`, `certificateDocument` (FileReference). Lives as a `DataObject` with `digitalObjectType = "calibration-certificate"` — folds into the FAIR6 (metadata-profile) enforcement once that ships. Size: S (template + N1 picker entry + UI badge "calibration valid until X").
- **CALIB1-LINK (new):** New SemanticAnnotation predicate `shepard:usedInstrumentCalibratedBy` connecting a measurement DataObject to a CalibrationCertificate DataObject. Surfaces on the measurement DataObject's detail page as a "Calibration: ✅ valid as of run start" badge with click-through. Size: S (annotation + UI). Combined: S–M.

**Adjacent existing rows.** AAS1 family (Asset Administration Shell — equipment as an AAS submodel) is the longer-term shape for equipment metadata — but it's heavier than what DaMaST needs now. CALIB1 above is the **lightweight bridge**: ship it as a SHACL template now, migrate to AAS submodel when AAS1 matures.

**Blocks.** Doesn't block live MFFD ingest. **Blocks Wave-2 quality-gate primitives** (Layer 4 per `aidocs/strategy/100 §3`) and the DIN EN 9100 audit readiness `aidocs/agent-findings/manufacturing-quality.md §5` calls out. Required before MFFD can publish under the "EN 9100-grade lineage" claim.

---

### §3.6 Data Lifecycle — retention + storage management (PARTIAL gap)

**What the user needs.** Every DaMaST questionnaire said "no strategy" for end-of-life data; the deck names Tivoli Storage Manager as a negative example. MFFD specifically: AFP runs produce ~GB-per-run files; over a campaign that's TB; some are intermediate (consolidation-attempt traces that the next pass supersedes) — there's no built-in mechanism today to mark "this intermediate is superseded by run N+1".

**What's there.** `project_storage_management.md` notes SM1 design (StorageProvider SPI; orphan default = infinite grace + nag notifications; onExceed default = WARN; Neo4j retention policies). FS1h (per-Collection storage choice) is parked. PROV1f ships a Nightly retention-TTL job already for the **provenance** layer (730 days default).

**Proposed.** Promote SM1 from memory note to a real backlog row:

- **SM1-PROMOTE (new from existing design note):** Lift SM1 into `aidocs/16` as an active umbrella with sub-rows SM1a (StorageProvider SPI extraction), SM1b (retention policy CRUD on `:RetentionPolicy` singleton or per-Collection), SM1c (orphan-grace + nag via NTF1 once that lands), SM1d (UI surface for retention dashboards). The data-lifecycle ask is the deferred-but-tracked thing. Size: per sub-row.

**Adjacent existing rows.** NTF1 (notification system design — also in `project_notification_system.md`) is the prerequisite for the nag pattern. FS1 family covers the storage substrate.

**Blocks.** Doesn't block live MFFD ingest. **Blocks long-term MFFD storage hygiene** (TB-per-campaign accumulation with no supersession marker); deserves a DESIGN doc before any row lands.

---

### §3.7 Watch list — MEDIUM MFFD-relevance Gap=YES items

These deserve a backlog row but aren't in MFFD's critical path. Listed for the curator to file at discretion:

- **Derived-data license inheritance.** When a DataObject is created via `prov:wasDerivedFrom` an existing one, the child should default-inherit the parent's license + accessRights unless explicitly overridden. Today this is a manual step. Proposed: hook into `PROV1c2` (pre-existing inheritance heuristics) + FAIR1. Size: S. **Doesn't block MFFD** — but MFFD's reorganised DataObjects post-AI-forging pass will need this consistency to be auditor-credible.

- **Living DMP + Actionable DMP.** FAIR7 (DMP-snippet generator) ships a static Markdown export. The DaMaST deck named "Actionable DMPs: trigger automated processes from DMPs" as a long-term target. Proposed: DMP-as-DataObject with `digitalObjectType="dmp"` + a `:DmpRule` shape that drives FAIR3 defaults / FAIR9 sensitivity defaults / retention policies. Size: M after FAIR7 lands. **Doesn't block MFFD** — but generalises the MFFD-bespoke discipline.

- **Hot/cold tier transitions.** Garage is one warm tier. DaMaST asks for archived vs active distinction — extend SM1-PROMOTE with an Archive-tier provider. Out of MFFD timeline.
- **Helmholtz KPI for Software & Data export.** `GET /v2/admin/helmholtz-kpi` endpoint emitting the metrics Helmholtz tracks. Cheap once FAIR4 + UH1d + PROV1c are in place. Size: S. Unlocks DLR-level reporting.
- **F-UJI alignment** (FAIR8). Already queued.
- **Binary-format diff (CAD versions).** RY-SPB ask; probably a viewer-plugin (CAD1) not substrate. Out of MFFD critical path.

---

## §4 — End-state summary

Six MFFD-HIGH gaps are currently uncovered in the SSOT backlog:

1. **AI1f-PARFILE** — extension of AI1f to TAU parfile + MAPDL parameter files (Effort: M)
2. **FAIR9 + FAIR9-COMBO** — SENSITIVE / EXPORT_CONTROLLED tier on accessRights + combinatorial-sensitivity predicate (M + L)
3. **PERM-3TIER** — three-band permission model (general / specific / data metadata) (M–L)
4. **CRAWL1 + CRAWL1-MFFD** — discovery crawler plugin + ZLP-NFS preset (L + S)
5. **CALIB1 + CALIB1-LINK** — calibration certificate as first-class SHACL template (S–M)
6. **SM1-PROMOTE** — promote storage-management design from memory note to active umbrella (per sub-row)

Of these:

- **None blocks the live MFFD ingest itself.** The v15.x pipeline + the FAIR1-5 + PROV1 family are sufficient for ingest.
- **Three (FAIR9, PERM-3TIER, CRAWL1)** block the **rollout plan from Wave 1 onwards** — cross-cell discovery + cross-cell sensitivity gating + share crawling are wave-1/2/3 dependencies per `aidocs/strategy/100`.
- **One (CALIB1)** blocks the **MFFD DIN EN 9100 publishability** claim.
- **The rest** are MFFD-adjacent quality improvements.

Recommended sequencing: ship **CALIB1 + FAIR9** in the next sprint (smallest + biggest publishability gain); promote **CRAWL1** to design-doc + queue for after #126 (importer plugin) baseline lands; queue **PERM-3TIER** behind a dedicated design doc (the permission model deserves a deliberate ADR not a fast PR); **SM1-PROMOTE** is the operator-runbook door — open it as a design row first.

---

## §5 — Sources

- DaMaST Workshop Protokoll v1, DLR Göttingen, 2025-10-23/24 (93 pages, uploaded to AI working memory 2026-05-23 as `1c41afc6-20260523__v1.pdf`); pages 4-11 = DG topic taxonomy; pages 17-93 = institute questionnaire responses (AS-BS, AS-GÖ, AS-KP, RA, RB-MRB, RY-SPB, RY-SRT)
- `aidocs/frontend/01-user-research-findings-2024.md` — 5-phase user-journey canonical frame
- `aidocs/16-dispatcher-backlog.md` — SSOT for backlog rows cited
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — status-symbol authority
- `aidocs/34-upstream-upgrade-path.md` — ledger of what's added on top of upstream v5.2.0
- `aidocs/strategy/100-shepard-bt-zlp-rollout-plan.md` — Wave 0–5 rollout sequencing
- `aidocs/agent-findings/manufacturing-quality.md` — DIN EN 9100 / NCR / calibration prior persona-audit findings
- `aidocs/agent-findings/research-data-manager.md` — FAIR-side prior audit
- Anonymity discipline: mirror of `project_ux_research_2024_anonymity.md`; institute-level only, never individual

(no individuals are named anywhere in this committed file — apply the same filter to all derivative work)
