# Manufacturing Quality Readiness Assessment
**Role:** Lead IME / AQE — DLR Augsburg MFFD Programme
**Date:** 2026-05-21
**Platform version assessed:** Shepard noheton/main (upstream 5.2.0 fork)
**Assessment target:** Fitness for use in a DIN EN 9100 / EASA Part 21 G / Nadcap aerospace manufacturing environment, specifically the MFFD CF/LMPAEK thermoplastic fuselage shell at DLR Augsburg.

---

## 1. What I Found — Actual Data Model, Status Values, and LUMEN Predecessor Chain

### Data model (entity layer)

The core entity is `DataObject` (Neo4j node), extending `AbstractDataObject → VersionableEntity → BasicEntity`. Relevant fields:

- `name` (String, required)
- `description` (String, free-text, supports Markdown via rich-text editor)
- `status` (String, unvalidated free-text in the Neo4j layer; the IO layer declares an enum hint: `DRAFT | IN_REVIEW | READY | PUBLISHED | ARCHIVED`)
- `attributes` (Map<String,String> — flat key-value, values always strings, no typed fields)
- `revision` (long, monotonically increasing write counter, server-managed)
- `appId` (UUID v7, stable identifier)

Relationships on DataObject:
- `HAS_SUCCESSOR / HAS_PREDECESSOR` — directed process chain (Predecessor → this node → Successor)
- `HAS_CHILD / HAS_PARENT` — structural BOM hierarchy
- `HAS_REFERENCE` — links to data containers (timeseries, files, structured data, HDF5, video)
- `HAS_LABJOURNAL_ENTRY` — free-text lab journal entries per DataObject

Status is stored as a plain `String` field in Neo4j. There is **no server-side status machine**: any status value can be written to any DataObject by anyone with Write permission, in any direction (PUBLISHED → DRAFT is as easy as DRAFT → PUBLISHED). No transition guards, no role restriction on status change, no immutability enforcement when PUBLISHED.

### Actual status vocabulary (hardcoded in IO + frontend)

```
DRAFT | IN_REVIEW | READY | PUBLISHED | ARCHIVED
```

These are the only five values displayed in the UI select dropdown and mentioned in the API doc. Nothing else is enforced at the Neo4j or service layer — the backend stores whatever string is sent.

### LUMEN showcase — the TR-004 through TR-006 anomaly/repair/re-test chain

The LUMEN seed (`examples/lumen-showcase/seed.py`) builds the following Predecessor/Successor chain:

```
TR-001 → TR-002 → TR-003 → TR-004 (vibration anomaly)
                                    └─ [Child] Anomaly Investigation — TR-004 Fuel Turbopump
                                             (parentId = TR-004, predecessorId = TR-004)
TR-004 → TR-005 (hold day, bearing teardown)
TR-005 → TR-006 (re-test post bearing replacement)
            ^ also predecessorId = Anomaly Investigation
```

TR-004 carries the semantic annotation `shex:QualitySuspect` on its TimeseriesReference. TR-005 has `HOLD_DAYS` flag (`is_fired: false`, `duration_s: 0`). The Investigation DataObject is a Child of TR-004 and a Predecessor of TR-006.

This **does model a simplified anomaly–investigation–repair–retest loop** but only through graph structure, not through a dedicated status/workflow concept. There is no NCR entity, no disposition field, no formal hold state on TR-005, and no blocking mechanism that prevents TR-006 from being created before TR-005 is resolved.

---

## 2. EN 9100 Readiness Assessment

| EN 9100 Requirement | Shepard Capability | Gap | Severity |
|---|---|---|---|
| **7.8.2 Traceability** — each product uniquely identified and traceable through manufacturing | `appId` (UUID v7) on every DataObject + Predecessor/Successor chain | Chain is traversable but no directed lineage walk API exists yet (queued as `aidocs/30`). An auditor must traverse manually or write Cypher. | MAJOR |
| **8.5.2 Identification and traceability** — traceability to material, equipment, and personnel | DataObject attributes (flat key-value), Lab Journal (free text), semantic annotations | No first-class equipment entity. Calibration certificate cannot be linked to a process run as a typed relationship — only as a free-text attribute or file reference. | CRITICAL |
| **8.4.3 Information for external providers** — control records for special processes | File containers can hold Nadcap certificates; structured data can hold process parameters | No special-process status field, no Nadcap approval number field, no built-in mechanism to flag a DataObject as "requires external provider certificate" | MAJOR |
| **8.6 Release of products and services** — product must not proceed until planned activities have been satisfactorily completed | READY status exists but has no enforcement — next process step DataObject can be created regardless of predecessor status | No gate: Shepard does not block creation of a successor DataObject if the predecessor is in DRAFT or IN_REVIEW. This is a structural gap for quality-gate workflows. | CRITICAL |
| **8.7 Control of nonconforming outputs** — NCRs must identify, document, and disposition defective parts | No NCR entity. Modellable via attributes + semantic annotations (`shex:QualityFail`), but no structured disposition field (Accept/Scrap/Rework), no NCR number, no required-fields enforcement. | No native NCR concept. | CRITICAL |
| **8.7.3 Rework and repair records** — rework must be re-inspected; original records must be preserved | Rework loop can be represented as a branching Predecessor chain (see §5). Original DataObjects are never deleted (soft-delete only). Immutability is not enforced on PUBLISHED DataObjects. | PUBLISHED DataObjects can be silently overwritten (status returned to DRAFT by any Write user). No server-side lock. | MAJOR |
| **9.1.1 Monitoring, measurement, analysis and evaluation** — process performance monitoring | TimescaleDB timeseries + anomaly detection (AI1b rolling-median MAD) + quality scoring (AI1c, backend-only) | Quality score field exists on TimeseriesReference but is never surfaced in the UI. | MINOR (gap is UI-only) |
| **10.2 Nonconformity and corrective action** — root cause analysis must link defect to process | Predecessor chain + Lab Journal + semantic annotations can carry this narrative | No structured root-cause field. A DIN EN 9100 audit would expect a machine-readable causal link, not a free-text journal entry. | MAJOR |
| **9.2 Internal audit** — documented audit trail of who did what when | Provenance (`/v2/provenance/activities`) captures CRUD operations per entity with actor + timestamp. W3C PROV-O and metadata4ing export available. | Provenance captures Shepard-level actions (create, update, delete), not manufacturing-level actions (AFP run started, NDT completed, weld certified). Manufacturing actions would need to be explicitly seeded as provenance entries or DataObject-level annotations. | MAJOR |
| **7.6 Control of monitoring and measuring equipment** — calibration state traceable to process | No calibration entity. Calibration certificate can be uploaded as a file, but there is no typed `:CALIBRATED_WITH` edge linking equipment to a process DataObject. | No native calibration linkage. This is the single most important missing concept for Nadcap special-process traceability. | CRITICAL |
| **AS9102 First Article Inspection** — FAI report must document design intent vs. as-built | No "design intent" vs "as-built" distinction in the data model. Parent/Child models BOM structure only, not revision state. | No as-designed / as-built differentiation. | CRITICAL |
| **EASA Part 21 G — approval-holder responsibilities** — all conformity records must be held for defined retention periods | DataObjects cannot be deleted without admin action. Archival status is soft (no system-level lock). Retention policy engine is in design (SM1 in roadmap). | ARCHIVED status has no enforcement. No retention policy engine (SM1 is designed, not shipped). | MAJOR |

**Summary verdict:** Shepard is not ready for a primary EN 9100 quality record system without significant extension. It is ready to serve as a **data lake / research data management layer** that feeds a separate QMS, or as the **digital thread backbone** if the QMS writes conformity disposition events back to Shepard via the API.

---

## 3. NCR Workflow Design — How to Model It Today, What Is Missing

### What can be done today (workaround)

An NCR can be partially represented using a dedicated DataObject:

```
[AFP_Run_001 DataObject, status=READY]
    └── [Child] NCR-2026-AFP-001 DataObject
            attributes:
                ncr_number: "NCR-2026-AFP-001"
                defect_type: "FiberMisalignment" (or shex:FiberMisalignment IRI via annotation)
                disposition: "Rework"  (free text — no enforcement)
                severity: "Minor"
                responsible_engineer: "A. Reuter"
                ncr_opened: "2026-05-21T08:00:00Z"
                ncr_closed: ""  (empty until resolved)
            status: DRAFT (open) → IN_REVIEW (engineering review) → READY (disposition approved) → PUBLISHED (closed)
            Lab Journal: Investigation findings, engineering judgment narrative
            Semantic Annotations: shex:QualityFail, shex:Delamination, shex:UltrasonicCScan
```

The Lab Journal on the NCR DataObject carries the written record. The semantic annotations make it machine-searchable. The status progression approximates an approval workflow.

### What is missing

1. **No structured disposition field.** "Accept / Scrap / Rework / Use-As-Is" must go into a free-text attribute. It is not validated, not queried efficiently, and cannot gate downstream actions.
2. **No predecessor-status gate.** The successor process step DataObject can be created and set to READY while the NCR is still open.
3. **No required fields enforcement on the DataObject.** There is no mechanism to say "an NCR DataObject must have `ncr_number`, `disposition`, and `responsible_engineer` before its status can advance to READY."
4. **No NCR number sequence generator.** The number must be managed externally or entered manually.
5. **No notification trigger.** When an NCR status changes to IN_REVIEW, there is no built-in way to notify the disposition authority. The NTF1 notification system is designed but not shipped.
6. **No role-gated status transition.** Any Write user can close an NCR. EN 9100 requires that dispositions be made by an authorised person (Designated Engineering Representative or equivalent).

### What needs to be designed

A `shepard-plugin-quality` or `Q1` backend extension would need to introduce:
- A `:NonConformance` Neo4j entity (ncr_number, opened_by, opened_at, defect_description, disposition {ACCEPT|SCRAP|REWORK|USE_AS_IS}, disposition_authority, closed_at)
- A `:RAISED_AGAINST` edge from `:NonConformance` to `:DataObject`
- A transition guard on the `HAS_SUCCESSOR` creation path — if any open `:NonConformance` is raised against a DataObject, its successors cannot be created until the NCR is closed
- REST endpoints under `/v2/nonconformances/`
- Role-gated status transitions (only `quality-engineer` role can set disposition = ACCEPT)

---

## 4. Rework Loop Data Model — Branching Predecessor Chain Proposal

The Predecessor/Successor graph supports branching natively (a DataObject can have multiple predecessors). A rework loop is representable as a non-linear directed acyclic graph:

```
[AFP_LayupRun_001]  →  [NDT_Inspection_001 (FAIL)]
                              ↓
                       [Rework_001 (shex:Rework)]
                              ↓
                       [NDT_Inspection_002 (PASS)]
                              ↓
                       [Stringer_Weld_001]
```

Implementation:
- `NDT_Inspection_001.attributes.inspection_outcome = FAIL`, semantic annotation `shex:QualityFail`
- `Rework_001.predecessorIds = [NDT_Inspection_001.id]`
- `NDT_Inspection_002.predecessorIds = [Rework_001.id]`
- `Stringer_Weld_001.predecessorIds = [NDT_Inspection_002.id]`

The original failure record (`NDT_Inspection_001`) is never deleted — it is ARCHIVED with status `ARCHIVED` and remains in the graph. This satisfies the EN 9100 requirement that the original failure record is immutable (though today Shepard does not technically enforce immutability — see §2).

**What this gives you:**
- Full traceability: an auditor can walk backwards from `Stringer_Weld_001` and find every predecessor including the failed inspection and the rework event.
- The semantic annotation (`shex:QualityFail`) on the failed inspection DataObject is preserved in the graph and queryable.

**What this does not give you:**
- An automated block on progression past a failed NDT. Today, `Stringer_Weld_001` can be created even if `NDT_Inspection_001` has `shex:QualityFail`. The DAG structure is advisory, not enforced.
- A branching visualization. The current provenance UI shows a force-directed graph of PROV activities, not the Predecessor/Successor graph directly. The new `DataObjectProvGraph.vue` (untracked, not yet shipped) may help but is not feature-complete.

---

## 5. Status Vocabulary Extension — Proposed New Values

The current five values are research-lifecycle oriented (DRAFT → IN_REVIEW → READY → PUBLISHED → ARCHIVED). For aerospace manufacturing quality management, the following values are needed:

| Proposed Value | Justification | EN 9100 anchor |
|---|---|---|
| `NCR_OPEN` | Active non-conformance has been raised; downstream processing blocked. Distinct from DRAFT (which implies normal work in progress). | §8.7 Control of nonconforming outputs |
| `NCR_DISPOSITIONED` | Disposition decision made (Accept/Scrap/Rework/Use-As-Is) by authorised person; awaiting execution. | §8.7.3 |
| `ON_HOLD` | Formal hold applied — process cannot proceed. Different from NCR_OPEN: a hold may be precautionary (awaiting test results) not triggered by a confirmed defect. | §8.7, Special Process hold points |
| `REWORK` | Component is actively being reworked following a disposition of "Rework". | §8.7.3 |
| `REJECTED` | Component scrapped; no further processing. Record kept for traceability. Analogous to ARCHIVED but with explicit failure semantics. | §8.7 |
| `SUPERSEDED` | This DataObject represents an as-designed or design-intent record that has been superseded by a revision. Not deleted, not archived — still visible in lineage. | EASA Part 21 configuration management |
| `CERTIFIED` | Equivalent to PUBLISHED but with the explicit meaning that a conformity declaration has been made. Distinct because PUBLISHED in research context means "open for citation"; in manufacturing it means "I declare this meets the drawing". | EASA Part 21 G §21.A.129 |

**Implementation note:** Because status is stored as a free String in Neo4j with no server-side enum enforcement, adding new values requires only: (a) updating the `enumeration` hint in `AbstractDataObjectIO`, (b) updating the frontend dropdown, and (c) adding transition guards in `DataObjectService`. No schema migration needed. The hard part is designing the transition guards and role gates.

---

## 6. Calibration Traceability Design

This is the most critical gap for Nadcap and EN 9100 §7.6. The MFFD AFP robot head has a Coherent LIVELINE laser whose calibration must be traceable to an NPL/PTB standard.

### What Shepard can do today (workaround)

Create a `Collection` called "Calibration Records" with DataObjects per equipment item:
```
[AFP_LaserHead_SN_2024-007 DataObject]
    attributes:
        equipment_id: "AFP-LH-007"
        calibration_due: "2026-12-01"
        calibration_authority: "DLR-ILS Metrology"
    File Container:
        cal_cert_2026-03.pdf  (calibration certificate, SHA-256 verifiable)
    status: PUBLISHED (calibrated, in-service)
```

Then link the AFP process run DataObject to the calibration record via a DataObject-to-DataObject reference (`POINTS_TO` relationship in the graph). The reference does not carry a typed edge label (it would be rendered as "Related Entity" in the UI), so the semantic meaning must be carried in a Lab Journal entry or attribute.

### What needs to be designed for proper traceability

A `EquipmentCalibration` concept (or a `/v2/` endpoint family):

1. `:Equipment` Neo4j entity — serial number, equipment type, owner, location
2. `:CalibrationRecord` Neo4j entity — calibration date, calibration authority, due date, standard reference (UKAS/DAkkS cert number), calibration status {CURRENT|EXPIRED|SUSPENDED}
3. `:CALIBRATED_BY` edge from `:CalibrationRecord` to `:Equipment`
4. `:USED_CALIBRATED_EQUIPMENT` edge from `:DataObject` (process run) to `:Equipment` (at calibration state valid at that time — time-indexed)
5. A process-run creation guard: if an `:Equipment` linked to the process run has `CalibrationRecord.status = EXPIRED`, the DataObject cannot be promoted to `READY`.

**Simplest viable approach with current Shepard (no code changes):** Use structured data containers to store calibration records as JSON, and use semantic annotations (`shex:CalibrationMeasurement`, `shex:CalibrationTarget`) to link equipment DataObjects to process run DataObjects. Not machine-enforced but auditable.

---

## 7. Shop Floor UI Requirements — Hand to UX Team

The current UI is designed for researchers at a desk. An MFFD shop floor IME environment has different constraints:

### Physical environment
- 27" ruggedized touchscreen mounted at head height on the AFP robot gantry cell
- Operator wearing nitrile gloves (fine touch unreliable)
- Background light from AFP laser housing (bright, washes out low-contrast UI)
- Noise level 85+ dB (no audio feedback useful)

### Required UI elements not present today

1. **Large-target status button.** The current status dropdown is a standard Vuetify `v-select` (32px touch target). Shop floor needs a minimum 80px tap target. A dedicated "Advance Status" full-width button should be the primary CTA on the DataObject page.

2. **Current calibration state banner.** Before starting a layup run, the IME must confirm the laser head calibration is current. A green/amber/red calibration status banner should appear at the top of the AFP_Run DataObject creation form, reading from the linked equipment's calibration record.

3. **Hold/NCR quick-raise button.** A single large red "Raise NCR / Place Hold" button should be reachable in at most 2 taps from any DataObject view. Today an NCR requires navigating to child DataObject creation, filling multiple fields, adding semantic annotations — too many steps for a gloved operator who just spotted a defect.

4. **Process chain position indicator.** The operator needs to know "where in the process chain is this component right now." A linear step indicator (AFP → Weld → NDT → Weld_Frame → ...) with the current DataObject highlighted, showing predecessor status at a glance. Not available in the current UI.

5. **Scan-to-find.** MFFD panel serial numbers are barcoded. The shop floor terminal must support a barcode scanner input (keyboard wedge) that opens the relevant DataObject immediately. This is a URL-scheme question (`shepard://dataobject?q={barcode}`) that the current app does not support.

6. **Offline indicator.** The AFP cell may be in a Faraday-shielded area with intermittent WiFi. The UI must display a clear "OFFLINE — changes will not save" banner. The current app has no offline detection.

7. **Mandatory-fields enforcement at DataObject create.** A Templates-based DataObject creation form that refuses to submit until all required fields (per the ShepardTemplate definition) are populated. This exists at template level (T1a–T1f) but the IME's view of "required" may differ from the researcher's — it needs to be configurable per Template with a "shop floor mode" flag.

8. **QR code on completed process records.** Once a DataObject reaches `CERTIFIED` status, the UI should offer to generate and print a QR code linking to the DataObject's `/v2/` URL, for physical labelling of the MFFD panel.

---

## 8. What Shepard Gets Right for Manufacturing

Despite the gaps, Shepard has several foundations that are directly valuable for aerospace manufacturing:

1. **Predecessor/Successor graph natively models the process chain.** Neo4j's native graph traversal means "find all predecessors of this DataObject back to raw material" is a depth-bounded graph query, not a series of SQL joins. This is exactly the right data structure for a digital thread.

2. **Semantic annotation with pre-seeded manufacturing ontology.** The `shepard-experiment.ttl` ontology already ships with: `ManufacturingProcess` (AFP, Welding, Bonding), `DefectType` (Delamination, FiberMisalignment, ForeignObject), `InspectionMethod` (UltrasonicCScan, Thermography, VisualInspection), `QualityFlag` (QualityPass/QualityFail/QualitySuspect). These are exactly the concepts an MFFD quality record needs. They are queryable via SPARQL (N1f, backend shipped).

3. **Provenance capture is automatic.** Every DataObject create/update/delete is captured as a PROV-O activity with actor + timestamp + target. This gives a free audit log of "who changed the status of AFP_Run_001 and when." W3C PROV-O export and metadata4ing profile export are both available.

4. **Immutable history via snapshots.** Collection-level snapshots (V2a–V2e) provide point-in-time views of the entire collection state. An auditor can request a snapshot at any date and see the exact DataObject states at that moment. This is EN 9100 audit trail capability at collection granularity.

5. **RO-Crate export.** The export generates a standards-compliant Research Object Crate that includes all DataObject metadata, provenance, permissions, and annotations. This is a viable format for submitting a digital quality record package to a certification authority.

6. **File container with SHA-256 versioning.** Every uploaded file carries a SHA-256 hash and version counter. A calibration certificate uploaded to Shepard is content-addressed and tamper-evident (though the system itself has no external signature verification).

7. **API-key-based access for automated ingest.** The AFP robot controller can write process parameters directly to Shepard via the API at end-of-run using an API key with scoped Write permission. This avoids manual data entry, which is the primary source of transcription error in paper-based QMS.

8. **Templates (T1a–T1f)** enforce a minimum required-field structure at DataObject creation time, providing a machine-checked data completeness gate that replaces the "did the operator fill in the paper traveller?" question.

---

## 9. Opportunities

In order of implementation complexity vs. compliance value:

### Near-term (configuration only, no code)

1. **Define an MFFD-specific ShepardTemplate** for each of the four process steps. Use the required-fields list from AS9102 as the mandatory attributes. Operators who create a DataObject from the template cannot submit without filling them. Estimated effort: 2 days.

2. **Establish a Calibration Collection** per the design in §6. Link equipment DataObjects to process run DataObjects via DataObjectReference. Write a Lab Journal convention ("calibration cert at time of run: {cert_ref}"). This gives an auditable trail without any code changes. Estimated effort: 1 day + calibration certificate data entry.

3. **Use semantic annotations for quality gates.** Annotate every completed inspection DataObject with `shex:QualityPass` or `shex:QualityFail`. Use SPARQL (`GET/POST /v2/semantic/{repoAppId}/sparql`) to query "any DataObject in Collection X with QualityFail that has a successor not annotated QualityPass after a rework step?" This gives an automated check that can be scripted. Estimated effort: 1 week.

### Medium-term (frontend + light backend)

4. **Ship the `DataObjectProvGraph.vue` component** (already untracked in the working tree). Completing this gives the process chain visualization the IME needs without a new backend feature. Estimated effort: 1–2 weeks.

5. **Add NCR status values to the enum** (`NCR_OPEN`, `ON_HOLD`, `REJECTED`, `CERTIFIED`). Update the frontend dropdown and add a service-layer guard that prevents `status → NCR_OPEN` if the user is not in the `quality-engineer` role. Light backend change, no new entities. Estimated effort: 1 week.

6. **Extend ShepardTemplate** with "shop floor mode" flag that renders a large-target mobile-first layout instead of the standard expansion-panel layout. Existing template infrastructure (T1a–T1f) makes this feasible without new entities. Estimated effort: 2–3 weeks.

### Medium-term (new backend features)

7. **Predecessor-status gate.** Add a configurable rule to `DataObjectService.createDataObject`: if any predecessor DataObject has status in `{NCR_OPEN, ON_HOLD}`, return 409 with a reason body. Toggle via `FeatureToggleRegistry` so operators can disable it during data migration. Estimated effort: 1 week backend + tests.

8. **Typed equipment-calibration relationship.** Introduce a `/v2/equipment/` endpoint family (or `shepard-plugin-calibration`) with `:Equipment` and `:CalibrationRecord` entities. Link process DataObjects to equipment at run time. This is the most impactful single change for Nadcap compliance. Estimated effort: 3–4 weeks.

### Longer-term (plugin-first per CLAUDE.md)

9. **`shepard-plugin-quality`** — full NCR lifecycle, disposition authority roles, transition guards, notification triggers (NTF1 dep), QR code generation. This is the correct structural home for a quality management layer per the plugin-first principle. The backend's core primitives (DataObject, status, Predecessor chain, Provenance, Templates) provide all the necessary hooks; the plugin adds the domain-specific workflow on top.

---

## 10. Real-World Impact — What Certification It Could Support, What It Cannot Yet

### What Shepard can support today

- **DFG / HMC FAIR data requirements.** Shepard was designed for this. FAIR compliance is strong: persistent identifiers (appId/PID via KIP1a), machine-readable metadata (semantic annotations + SPARQL), open access control (per-collection permissions), reuse-enabling exports (RO-Crate). Full FAIR capability.

- **Research data management layer for EN 9100 programmes.** As a supporting system that stores and organises raw manufacturing data (AFP sensor timeseries, NDT scan files, process parameter CSVs), Shepard is immediately deployable. It does not need to be the system of record for conformity declarations — it can be the system of record for the underlying data.

- **Internal audit trail.** Provenance captures who changed what when. The Lab Journal provides a structured narrative. Snapshots provide point-in-time views. Combined, these support an internal audit function without requiring a separate document management system.

- **Digital thread backbone for research programmes.** For a DLR internal research programme (not EASA certification), Shepard's predecessor/successor chain + semantic annotations can be the digital thread. Researchers can trace from a structural simulation result back through AFP layup parameters to raw material properties.

### What Shepard cannot yet support

- **Primary quality records system for EASA Part 21 G conformity.** The absence of: (a) enforced immutability on PUBLISHED/CERTIFIED records, (b) a native NCR entity with disposition workflow, (c) calibration traceability as first-class entities, and (d) role-gated status transitions means Shepard cannot be the authoritative system for a conformity declaration record under current regulation. Note: DataObject deletion requires Write permission (not admin), so records are not admin-delete-only — though soft-delete and the absence of a retention enforcement engine still apply.

- **Nadcap special process records.** Nadcap auditors require: traceability of equipment to calibration certificate (with calibration date and expiry), qualification records for the process (approved parameters with deviation tracking), and records of personnel qualification. None of these are first-class entities in Shepard.

- **AS9102 First Article Inspection reports.** An FAI requires a formal comparison of design intent vs. as-built for every characteristic. The "as-designed vs. as-built" distinction does not exist in Shepard's data model. Parent/Child models BOM structure only; there is no design revision entity.

- **Blocked-progression quality gate for production.** Without the predecessor-status gate described in §9, nothing prevents a shop floor operator from creating the next process step DataObject while an NCR or hold is active on the previous step. For a production line (as opposed to a research programme), this is unacceptable.

---

## 11. What Surprised Me

1. **The semantic ontology coverage is exceptional for a research platform.** `shepard-experiment.ttl` ships with `shex:Delamination`, `shex:FiberMisalignment`, `shex:UltrasonicCScan`, `shex:AFP` as SKOS concepts. For an aerospace manufacturing programme, the vocabulary needed for annotation is already there. This is not common in research data platforms and suggests the team has genuine manufacturing domain awareness.

2. **The status field has no server-side enforcement whatsoever.** I expected at minimum a closed enum. The status is stored as a plain `String` in Neo4j. Any string can be written. The enum hints in the IO layer are purely advisory. A PUBLISHED DataObject can be set back to DRAFT by any Write user in two API calls. This is the single largest gap between Shepard's current design and aerospace quality record requirements.

3. **Provenance is PROV-O native and exports to metadata4ing.** The `metadata4ing` profile on the provenance export (from ONT1b) maps activities to `m4i:ProcessingStep` — exactly the concept an MFFD process chain uses. This is a significant capability that most research data platforms don't have. Combined with the SPARQL proxy, a skilled operator can do sophisticated provenance queries without additional tooling.

4. **Snapshots are collection-granularity, not DataObject-granularity.** This surprised me. A `Snapshot` captures the state of an entire Collection at a point in time. For manufacturing purposes, you often need a snapshot of a single DataObject (the state of the process run at sign-off). The revision counter on `VersionableEntity` provides per-entity versioning, but there is no "DataObject-at-revision-N" retrieval endpoint — only collection-level snapshots. This means retrieving "what were the attributes of AFP_Run_001 when the conformity stamp was applied" requires a collection-level snapshot taken at that exact moment, not a targeted DataObject query.

5. **The LUMEN anomaly sequence is structurally correct but semantically incomplete.** TR-005 (hold day) has no hold state. It has `is_fired: false` as an attribute and no timeseries, but its `status` field is not set to anything — it is null. An auditor looking at the DataObject list would see TR-005 as an entity with no status, not as an entity under hold. The data is all there; the semantic labelling of the hold state is missing because the status vocabulary has no `ON_HOLD` value.

---

*Assessment completed by IME/AQE agent. Findings are based on static code analysis of the Shepard backend (Java/Neo4j/Quarkus), frontend (Nuxt 3/Vuetify), seed script, and ontology files. No live system interaction was performed.*
