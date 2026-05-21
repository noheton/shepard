# Manufacturing & Quality Engineering — Cross-Agent Debate
**Author:** Industrial Manufacturing & Quality Engineer  
**Date:** 2026-05-21  
**Inputs read:** UX, Ontologist, API, Manufacturing, RDM, Strategy, Analytics, Ecosystem — all proposal sets  
**Lens:** EN 9100:2016 / EASA Part 21 G / DIN EN 9100 audit readiness

---

## Top 5 I'm Championing

### Champion 1 — Status Machine with Immutability Lock (Manufacturing P1 + P2, merged)

**EN 9100 clause:** §8.6 (Release of Products and Services), §8.7 (Control of Nonconforming Outputs)  
**EASA Part 21 G:** Subpart G §21.A.239 (Design Assurance System — documented conformity decisions must be immutable)

This is the single most critical gap between Shepard's current design and an auditable quality system. Status is a plain `String` in Neo4j with zero server-side enforcement. Any Write user can flip `CERTIFIED → DRAFT` at any time, silently erasing the conformity record. That is not a UX annoyance — it is a §8.6 audit finding that halts production certification.

The canonical implementation requires **all four layers** — no partial version passes:

1. **Vocabulary extension** (seven new values: `NCR_OPEN`, `NCR_DISPOSITIONED`, `ON_HOLD`, `REWORK`, `REJECTED`, `SUPERSEDED`, `CERTIFIED`). No schema migration needed.
2. **Role-gated transitions**: `status → CERTIFIED` and `status → REJECTED` require the `quality-engineer` role. `NCR_OPEN → NCR_DISPOSITIONED` also requires `quality-engineer`. Regular Write users get the research lifecycle only (`DRAFT → IN_REVIEW → READY → PUBLISHED`).
3. **Immutability lock on CERTIFIED/PUBLISHED**: Any PATCH that would regress status requires an explicit `POST /v2/data-objects/{appId}/unlock` with a mandatory reason body, logged as a PROV-O activity. The role required for unlock is `instance-admin`. Note: Strategy Advisor P8 misses this — it gates unlock on `instance-admin` without the positive `quality-engineer` release authority. Both roles are needed: `quality-engineer` to certify forward; `instance-admin` to unlock backward with an audit trail.
4. **Predecessor-status gate**: Creating a successor DataObject when any direct predecessor carries `NCR_OPEN` or `ON_HOLD` returns `HTTP 409 Conflict` with a structured body listing the blocking predecessors. FeatureToggle-gated (default OFF for migration safety).

Proposals from UX (P7), Ontologist (P6), Strategy (P8), and Analytics (P3) all address pieces of this. Manufacturing P1 + P2 are the only proposals that specify all four layers together. Champion the merged Manufacturing version; redirect the others to it.

**Why now:** The MFFD AFP dataset arrives approximately 2026-05-26. If ingested without a status machine, the first quality records will be structurally non-compliant and retroactively uncorrectable without a data migration.

---

### Champion 2 — Predecessor-Chain Ancestor Walk (API P4 canonical shape, Manufacturing P5 DAG frontend)

**EN 9100 clause:** §7.8.2 (Technical Data), §8.5.2 (Identification and Traceability)  
**EASA Part 21 G:** Subpart G §21.A.239 — traceability to design data must be complete and unbroken

Six proposals independently identify the same structural gap: `DataObjectProvGraph.vue` truncates at 6 predecessors (`slice(0,6)`, lines 86 and 106), and there is no server-side recursive ancestor walk. A 4-hop MFFD chain (AFP ply → layup run → material batch → supplier certificate) silently drops nodes 5 and 6 when they exist. An auditor who sees a chain that "ends" at node 6 has no way to know whether the chain is complete or whether Shepard hid the rest.

This is not a UX enhancement. A truncated lineage walk is an §7.8.2 audit finding.

**Canonical implementation** (merging API P4 wire shape + Manufacturing P5 DAG + RDM P11 Cypher):

```
GET /v2/data-objects/{appId}/ancestor-chain
  ?maxDepth=20          // default 10, max 50
  &direction=predecessors  // | successors | both
  &format=graph          // | csv | ro-crate

→ 200 {
    "root": "appId",
    "depth": N,
    "truncated": false,
    "chain": [
      { "depth": 0, "dataObject": DataObjectV2IO },
      { "depth": 1, "dataObject": DataObjectV2IO, "via": "HAS_PREDECESSOR", "redacted": false },
      ...
    ]
  }
```

Key engineering constraint: items the caller lacks Read permission on must appear as `{"depth": N, "redacted": true, "appId": "..."}` — the chain depth must be visible even when content is restricted. An auditor who sees `{"redacted": true}` at depth 4 knows to request access; an auditor who sees a chain that silently terminates at depth 3 assumes completeness.

Frontend: dagre layout (Manufacturing P5), replacing the current force-graph. The rework loop (AFP → NDT FAIL → Rework → NDT PASS → Weld) is a DAG, not a network; force-graph layouts obscure the branching structure. Status color-codes nodes: `NCR_OPEN` = red, `CERTIFIED` = green, `REWORK` = amber. Remove the `slice(0,6)` cap immediately.

Export: `?format=csv` returns `name, appId, status, date, predecessor_appId` — the audit package an EASA conformity submission needs without post-processing.

---

### Champion 3 — Ingest Provenance Stamp (Unified for sTC + Hotfolder)

**EN 9100 clause:** §7.6 (Control of Monitoring and Measuring Equipment), §7.5.3 (Control of Documented Information), §8.5.2 (Identification and Traceability)

No peer agent proposal addresses this fully. This is the gap that makes all other quality features hollow: if ingest records from the shop-floor IPC (sTC timeseries adapter and hotfolder file adapter) do not carry a complete provenance stamp, every subsequent DataObject in the chain is untraceable to the calibrated equipment state that produced the raw measurement.

**Required stamp fields for every ingested record — both sTC and hotfolder — in the unified ingest SPI:**

| Field | EN 9100 clause | Notes |
|---|---|---|
| `sourceAdapter` | §7.5.3 | `sTC \| hotfolder \| j2e \| manual-upload` — distinguishes trust levels for NCR routing |
| `equipmentId` | §7.6 | Which instrument/PLC/sensor produced the data |
| `calibrationCertId` | §7.6 | Snapshot of the cert ID *at the moment of acquisition*, not a live link |
| `calibrationValidUntil` | §7.6 | Snapshot, not computed at read time — §7.6 requires the state when measured |
| `operatorId` | §7.5.3 | Who was responsible at the IPC at acquisition time |
| `shiftId` | §7.5.3 | Which shift/session (links to operator schedule) |
| `cellId` | §8.5.2 | Which physical manufacturing cell or test bay |
| `acquisitionTimestamp` | §7.5.3 | When measured — separate from ingest/upload timestamp |
| `originalFilename` | §7.5.3 | For hotfolder; forensic link to network share |
| `contentHash` (SHA-256) | §7.5.3 | File integrity; hotfolder must hash before ingestion |
| `dataClass` | §8.7 | `RAW_MEASUREMENT \| COMPUTED \| MANUALLY_KEYED` — determines NCR routing |

These fields do not belong only on the routing rules — they must be stamped onto the resulting DataObject attributes (or a companion `IngestProvenance` entity linked via PROV-O `prov:used`) so an auditor reading any DataObject can reconstruct where its raw data came from without chasing the ingestion log.

**Specifically for the Ansible/IPC dashboard:** the Grafana dashboard must surface `calibrationValidUntil` per instrument as a countdown and alert when any instrument used in the current or upcoming shift is within the configurable warning horizon (Manufacturing P4's `CalibrationConfig.reminderHorizonDays`). Without this, an operator runs a measurement with expired calibration and the audit failure is discovered weeks later, not at the point of work.

---

### Champion 4 — Equipment Calibration Plugin with Core Guard (Architecture Correction)

**EN 9100 clause:** §7.6 (Control of Monitoring and Measuring Equipment)  
**Nadcap:** Measurement Systems Analysis (MSA) requirements for special-process verification

Manufacturing P4 proposes building calibration inside `shepard-plugin-aas`. Ontologist P10 proposes `shepard-plugin-calibration`. I champion the Ontologist's shape with an architecture correction.

**The case against embedding calibration enforcement in `shepard-plugin-aas`:** EN 9100 §7.6 compliance cannot be contingent on whether `shepard-plugin-aas` is enabled. An institute that uses Shepard for quality management but has no AAS registry gets zero calibration enforcement. The AAS submodel is an *export format* for calibration data, not the enforcement mechanism. Coupling the gate to a plugin with its own deployment decision is a fragile compliance perimeter.

**Correct architecture:**
- `shepard-plugin-calibration` (Ontologist P10 shape) owns `Equipment` and `CalibrationRecord` entities, the REST surface (`/v2/equipment/...`, `/v2/equipment/{appId}/calibration-records`), and the NTF1 notifications for approaching calibration due dates.
- Core ships a minimal `EquipmentCalibrationGuard` SPI (one method: `boolean isCalibrationCurrentForEquipment(String equipmentAppId, Instant atTime)`) with a default no-op implementation that always returns `true`. When `shepard-plugin-calibration` is present, it registers the live implementation.
- The predecessor-status gate (Champion 1) calls this SPI hook before allowing a DataObject to advance to `IN_REVIEW` when a `USED_CALIBRATED_EQUIPMENT` attribute is present.
- `shepard-plugin-aas` may *optionally* export calibration records as IDTA Handover Documentation submodels — this is additive and not the enforcement mechanism.

The time-indexed edge `(:DataObject)-[:USED_CALIBRATED_EQUIPMENT {atDate: ISO-8601}]->(:Equipment)` is the critical design decision. The `atDate` property is what allows a future audit query: "what was the calibration state of AFP laser head AFP-LH-007 on 2026-03-14 at 09:37 when TR-006 was recorded?" A live link to a CalibrationRecord that has since been updated would not answer this question.

---

### Champion 5 — AnnotatableFile Bridge (Manufacturing P6 / Ontologist P4)

**EN 9100 clause:** §8.7 (Control of Nonconforming Outputs) — "the organization shall identify and control outputs that do not conform to requirements"  
**Nadcap requirement for NDT:** Defect must be recorded on the specific affected unit, not on the batch.

An MFFD AFP run produces approximately 200 STP layup-geometry files and a matching set of NDT C-scan images — one per ply. A defect detected on ply 47 must be locatable to that specific file, not annotated on the FileBundle as a whole. `chameo:LocalPorosityDefect` applied at FileBundle granularity is not a conforming NCR record under §8.7 because it does not identify the specific nonconforming output.

This is the structural analog of `AnnotatableTimeseries` applied to files. Manufacturing P6 and Ontologist P4 agree on the shape; the only decision is entity naming (both call it `AnnotatableFile`) and the foreign key (Ontologist uses GridFS ObjectId; Manufacturing uses `{fileName, fileContainerAppId}` pair). I recommend the GridFS ObjectId as the stable key — filenames can change; GridFS ObjectIds do not.

**Dependency order:** CHAMEO must be in the ontology manifest before `AnnotatableFile` is useful for NDT workflows. Champion 5 depends on CHAMEO + SSN/SOSA (Ontologist P3 + Manufacturing P10 — trivially merged) landing first.

---

## Top 3 I'm Challenging

### Challenge 1 — Strategy Advisor P8 "Quality Plugin Foundation" (Missing Positive Release Authority)

**What the agent claims:** "Status vocabulary + predecessor gate + immutability guard constitutes a minimum viable quality foundation."

**The audit failure mode:** Strategy P8's immutability guard gates unlock on `instance-admin` only. EN 9100 §8.6 requires not just that unauthorized people cannot undo certification — it requires that certification itself be performed by a positively identified, role-authorized person. The negative gate (prevent Write users from regressing) without the positive gate (require `quality-engineer` to certify) means any Write user can set `status → CERTIFIED` today (no role check on the transition forward). An auditor reviewing the certification record will find it was set by a researcher who had no quality engineering authorization.

**What must be added before this proposal is EN 9100-compliant:**
- Role check on `status → CERTIFIED` and `status → REJECTED` transitions (requires `quality-engineer` role)
- Audit trail entry on `POST .../unlock` that names the instance-admin who unlocked and the reason
- The `quality-engineer` role itself as a named role value (not a new Role entity, but a documented role string)

**Verdict:** REDIRECT to Manufacturing P2, which has all four layers. Strategy P8 is a useful summary but should not be implemented independently — it will ship a status machine that looks compliant but fails the positive-authority test.

---

### Challenge 2 — Ecosystem Advocate EP-02 "FAIR Scorecard Widget" (Audit-Unsafe Framing)

**What the agent claims:** "A FAIR health panel on the Collection detail page closes the FAIR compliance gap."

**The audit failure mode:** A FAIR scorecard that reports "80/100 — GOOD" on a Collection where:
- `status = null` on three DataObjects
- No calibration provenance on any ingested timeseries
- No `quality-engineer` release on any CERTIFIED record
- AnnotatableFile defect annotations absent from NDT scans

...creates a false compliance signal. EN 9100 auditors do not assess license fields or ORCID stamps. They assess process control documents, calibration traceability, and nonconformance records. A green FAIR score on structurally non-compliant quality records is worse than no scorecard — it provides false assurance to the operator.

**The specific concern for MFFD:** If the FAIR scorecard is visible in basic mode and the status machine enforcement (Champion 1) is not yet deployed, an operator may interpret a "FAIR: 80" score as "this data is ready" when the NCR workflow has not been completed and calibration provenance is absent.

**What must be added:**
- The FAIR scorecard must be clearly scoped to *data publication compliance* (funding mandates), not quality-management compliance.
- Add a separate "Quality Readiness" indicator (three checks: (1) all DataObjects have a non-null status; (2) no open NCRs on the chain; (3) all equipment calibration current as of last data acquisition). Display it alongside the FAIR score, not under it.
- Label the FAIR score clearly: "FAIR Publishing Score — measures compliance with open data mandates, not EN 9100 quality requirements."

**Verdict:** CHALLENGE — don't block it (the FAIR score is genuinely needed for HMC deadline), but require the scoping label and the separate Quality Readiness indicator in the same PR. The two scores must be visually distinct and neither should be titled simply "Quality."

---

### Challenge 3 — Analytics AI P4 / API P10 / UX P3 "PDF Auto-Annotation Accepted via Standard PATCH" (Controlled-Document Contamination)

**What the agents claim:** "Accepted suggestions are staged locally and applied via the existing attribute-update and annotation-create endpoints."

**The audit failure mode:** For quality-relevant DataObjects (JupyterHub J2e analysis notebooks, NDT inspection records, process parameter runlogs), an AI-suggested defect classification accepted by a researcher who holds Write but not `quality-engineer` role contaminates the controlled record with an AI-generated annotation that has no human expert review stamp.

Under EN 9100 §8.7, a defect classification on an inspection record is a controlled quality decision. If the AI suggests `chameo:LocalPorosityDefect` and a lab technician with Write role accepts it, the AnnotatableFile now carries a CHAMEO defect annotation with no indication that:
- The annotation was AI-generated
- The accepting user was not a qualified quality engineer
- No independent verification was performed

The `aiActivityAppId` field in the suggestion response is a provenance trail for *who suggested it*, but there is no `reviewedBy` stamp on the *accepted* annotation distinguishing it from a human-entered annotation with quality authority.

**What must be added before this is safe for quality-critical DataObjects:**
- The annotation PATCH that applies an accepted AI suggestion must stamp `generatedByAiActivityAppId` and `acceptedByUserId` on the resulting `SemanticAnnotation` node (two additive fields, small migration).
- For DataObjects with status ≥ `IN_REVIEW`, accepting an AI-suggested defect classification annotation must require the `quality-engineer` role or trigger an explicit "Review required" status flag (not auto-accept).
- The suggestion drawer must clearly label which suggestions are safe for any Write user vs. which require quality-engineer acceptance (proposals involving CHAMEO defect terms, `shex:QualityFail`, `shex:QualitySuspect` are quality-restricted; QUDT unit annotations are unrestricted).

**The J2e controlled-document exception:** JupyterHub notebooks auto-saved to Shepard are a specific case — any notebook whose execution produced a quality decision (e.g., "part passes NDT") must be gated on the status workflow (DRAFT → IN_REVIEW → PUBLISHED). The file-upload path must not bypass this by treating the notebook as a plain file with no status semantics. No proposal explicitly addresses this; it must be flagged.

**Verdict:** CHALLENGE — require the `generatedByAiActivityAppId` + `acceptedByUserId` stamp on accepted AI annotations before any quality-critical use, and require explicit documentation of which CHAMEO/quality annotation predicates are quality-role-restricted.

---

## What the Ingest Ecosystem Must Carry for EN 9100

Both sTC (PLC timeseries) and the hotfolder (network-share files) are source adapters in `shepard-plugin-ingest`. From an EN 9100 perspective, both must stamp the following provenance fields on every ingested record — consistently, not optionally:

```
Required on every ingested DataObject or Container:

sourceAdapter:          "sTC" | "hotfolder" | "j2e" | "manual-upload"
equipmentId:            "AFP-LH-007"           // which instrument produced this
calibrationCertId:      "DAkkS-2025-0043"      // cert ID snapshotted at acquisition
calibrationValidUntil:  "2026-09-30"           // snapshotted, not live-resolved
operatorId:             User.appId             // who was responsible at the IPC
shiftId:                "MFFD-2026-03-14-AM"   // shift/session reference
cellId:                 "ZLP-AFP-Cell-2"        // physical location
acquisitionTimestamp:   "2026-03-14T09:37:42Z" // when measured, not when uploaded
originalFilename:       "AFP_Run_007_TCP.csv"   // hotfolder: pre-rename name
contentHash:            "sha256:ab12..."        // hotfolder: hash before ingest
dataClass:              "RAW_MEASUREMENT"       // | COMPUTED | MANUALLY_KEYED
```

These fields belong on the DataObject `attributes` (or a dedicated `IngestProvenance` entity linked via `prov:used`) so they survive DataObject export and appear in the RO-Crate. Fields stored only in an ingest log that is rotated quarterly fail §7.5.3 record control requirements.

**Calibration snapshot rule:** The `calibrationCertId` and `calibrationValidUntil` fields must be snapshots taken at acquisition time, not live references to a CalibrationRecord that may be updated later. §7.6 requires that the calibration state *when the measurement was made* is traceable, not the calibration state today. A live foreign key to a CalibrationRecord whose `calibrationDueDate` field was retroactively extended would silently conceal an expired-at-measurement-time situation.

**The IPC dashboard requirement (not addressed by any proposal):** The Ansible/IPC Grafana dashboard must display a calibration expiry countdown per instrument that is active in the current or upcoming shift. This is the point-of-work signal that prevents an operator from beginning a measurement session with expired calibration. Without this, the audit failure is discovered by the Data Ontologist's Provenance Gap Detector (Ontologist P8 / Analytics P3) after the fact — too late for EN 9100 §7.6 preventive action.

---

## Merges I'm Calling

### Merge A — "Ancestor Chain Endpoint" (6 redundant proposals → 1 canonical)

UX P5, API P4, Manufacturing P5, Strategy P3, Analytics P6, RDM P11 all propose the same endpoint. Canonical shape:

- API wire shape: API P4 (typed `redacted` entries, direction param, truncated flag)  
- Cypher: Manufacturing P5 (`MATCH (start)-[:HAS_PREDECESSOR*1..{maxDepth}]->(anc)`)  
- DAG frontend layout: Manufacturing P5 (dagre, not force-graph)  
- CSV export: Manufacturing P5 + Strategy P3  
- RO-Crate format variant: Strategy P3  
- Fix `slice(0,6)` in `DataObjectProvGraph.vue`: in the same PR

One endpoint, one frontend component, one PR. All six proposals are resolved.

### Merge B — "FAIR Completeness Score" (5 proposals → 1 canonical)

UX P4, RDM P5, Strategy P1, Analytics P10, Ecosystem EP-02. Canonical scoring rubric: RDM P5 (most complete weighted-check table). Frontend ring: UX P4 (most detailed). Backend endpoint wire shape: API P6.

**Modification required (from Challenge 2 above):** Add a separate "Quality Readiness" indicator in the same component, scoped to EN 9100 checks (null-status DataObjects, open NCRs, calibration expiry), clearly labeled as distinct from the FAIR publishing score.

### Merge C — "CHAMEO + SSN/SOSA + MFFD Vocabulary" (Manufacturing P10 + Ontologist P3 + Ontologist P5)

Trivially merged — same manifest entries, same TTL pattern. Add in one PR:
- CHAMEO TTL entry (SHA-256 pinned)
- SSN/SOSA TTL entry (SHA-256 pinned)
- `mffd-process.ttl` (SKOS ConceptScheme per Ontologist P5)
- `pluto-mission.ttl` (Ontologist P5)
- `lumen-facility.ttl` (Ontologist P5)
- `skos:scopeNote` establishing QUDT as primary unit vocabulary (Ontologist P3)

### Merge D — "AnnotatableFile Bridge" (Manufacturing P6 + Ontologist P4)

Same entity shape, minor key difference resolved in favor of GridFS ObjectId as stable foreign key. One backend PR, one frontend PR.

### Merge E — "Material Batch as Graph Node" (Manufacturing P3 + Ontologist P2)

Both propose the same DataObject convention + `/v2/collections/{appId}/lot-lineage?lot_id={id}` endpoint. Manufacturing adds the `shepard-template-materialbatch.yaml` Template definition; Ontologist adds the seed update. Combine into one PR.

### Merge F — "Status Machine" (Manufacturing P1+P2 + Ontologist P6 + Strategy P8)

Manufacturing P1+P2 is the only complete specification. Ontologist P6 and Strategy P8 are subsets. Implement Manufacturing P1+P2; cite the others as resolved.

### Merge G — "FAIR Metadata Fields on AbstractDataObject" (RDM P1-4 + Ontologist P7 + Analytics P10 + Strategy P1)

All propose the same four fields: `license`, `accessRights`, `embargoEndDate`, `createdByOrcid` (+ `fundingReferences` in some). One backend PR, additive Neo4j properties, no migration. RDM P1-4 are the most detailed specifications; use those as the implementation reference.

---

## My Overall Priority Stack (Ordered by Compliance Risk — What Fails an Audit First)

### Tier 0 — Pre-MFFD Data Arrival (Before 2026-05-26) — Structural Integrity

These must land before real production data is ingested. If they don't, the first quality records are structurally non-compliant and cannot be retroactively corrected without a data migration.

1. **Status Machine with Immutability Lock** (Merge F — Manufacturing P1+P2 canonical)  
   *Audit failure if skipped:* Any CERTIFIED record can be silently regressed by a Write user. §8.6 finding on day 1 of audit. No amount of subsequent tooling recovers this.

2. **Ingest Provenance Stamp on sTC + Hotfolder** (new — no existing proposal covers this fully)  
   *Audit failure if skipped:* Every measurement record ingested before this ships is untraceable to a calibrated equipment state. §7.6 finding covering the entire ingested dataset.

### Tier 1 — Sprint 1 (First Two Weeks Post Data Arrival) — Traceability Closure

3. **Ancestor Chain Endpoint + DAG Frontend** (Merge A)  
   *Audit failure if skipped:* Lineage walks truncate silently at 6 hops. §7.8.2 traceability finding for any chain deeper than 6 DataObjects. The MFFD AFP process chain (material → layup → NDT → weld) is at minimum a 4-hop chain, and with material batch nodes it is 5–6.

4. **Equipment Calibration Plugin with Core Guard** (Champion 4 architecture)  
   *Audit failure if skipped:* No machine-enforceable link from a process DataObject to the calibration state of the equipment that produced its measurements. §7.6 finding for any measurement-intensive DataObject. The MFFD AFP dataset is measurement-intensive by definition.

5. **AnnotatableFile Bridge + CHAMEO in Manifest** (Champions 5 + Merge C)  
   *Audit failure if skipped:* NDT defect classification is at FileBundle granularity, not file granularity. §8.7 nonconforming output records do not identify the specific affected unit. CHAMEO terms are required vocabulary for those records to be machine-readable.

### Tier 2 — Sprint 2 (Weeks 3–4) — NCR and Material Traceability

6. **Material Batch as Graph Node + Lot-Lineage Endpoint** (Merge E)  
   *Audit failure if skipped:* "Which process runs consumed contaminated batch X?" requires a JSON full-text scan, not a graph traversal. §8.5.2 identification and traceability finding. Batch recall queries are standard EN 9100 audit scenarios.

7. **Predecessor-Status Gate** (Champion 1, sub-deliverable — gated on status machine landing first)  
   *Audit failure if skipped:* A successor DataObject can be created while its predecessor carries `NCR_OPEN`. §8.7 control of nonconforming outputs — the system does not prevent downstream processing on a part with an open nonconformance.

8. **AI Anomaly → NCR Auto-Raise Bridge** (Manufacturing P9)  
   *Audit failure if skipped:* AI1b detects anomalies but no structured NCR record is created. §10.2 nonconformity and corrective action requires documented evidence of the nonconformance — a `TimeseriesAnnotation` with `aiGenerated=true` is not a conforming NCR record.

### Tier 3 — Sprint 3 (Weeks 5–8) — FAIR and Shop-Floor Access

9. **Shop Floor Template Mode + Quick-Action Bar** (UX P7 + Manufacturing P7)  
   *Risk if skipped:* The MFFD shop-floor IME cannot use Shepard at a gloved-hand 27" touchscreen terminal. This is not an EN 9100 finding but a practical adoption blocker that makes the quality workflow theoretical rather than operational. §8.6 requires the quality gate at the point of work — that requires a usable UI at the point of work.

10. **FAIR Metadata Fields + Completeness Score with Quality Readiness Indicator** (Merge G + Merge B with Challenge 2 correction)  
    *Risk if skipped:* HMC Project Call 2026 deadline 06 July 2026. FAIR score failures affect funding compliance, not EN 9100 audit; ranked below §7–§8 items but time-critical for the HMC deadline.

11. **JupyterHub J2e Controlled-Document Status Gate** (gap not addressed by any proposal — must be added)  
    *Audit failure if skipped:* Analysis notebooks that produce quality decisions (NDT pass/fail) are controlled documents under §7.5.3. Auto-saving them as plain file uploads bypasses the DRAFT → IN_REVIEW → PUBLISHED status workflow. The J2e plugin must explicitly opt into the status machine, not treat the notebook as an uncontrolled file.

12. **Provenance Gap Detector** (Ontologist P8 / Analytics P3 merged)  
    *Value:* This is the audit-preparation tool — not a compliance primitive but the instrument that surfaces all the gaps above before an external auditor does. Run it before every quality review. Not an audit finding if absent, but a missed opportunity for preventive action.

---

## Gaps the Ecosystem Context Names That No Proposal Addresses

Three items from the ecosystem context preamble were either absent from all proposals or incompletely covered:

**Gap A — PR-Series In-Browser Stepper Must Call the Predecessor-Status Gate.** Manufacturing P1 ships the gate as a `DataObjectService` call. But the PR-series stepper (replacing SPW) invokes step-completion through a different path. If the stepper does not call the same gate, a process can advance through the stepper UI while a predecessor NCR is open — the SPW-to-PR migration ships a regression against the gate it is supposed to be retiring with. The stepper's step-completion action must call `DataObjectService.createSuccessorDataObject()`, not a bypass path. This must be verified in the PR-series design doc before the stepper ships.

**Gap B — J2e Notebooks as Controlled Documents.** Addressed partially in Challenge 3 above. No proposal specifies that JupyterHub J2e should emit DataObjects with initial status `DRAFT` and require the full status transition through to `PUBLISHED` before the notebook's quality decision is treated as conforming. This is a J2e plugin design requirement, not a core feature, but it must be explicitly specified in the J2e plugin's design doc before the plugin ships.

**Gap C — IPC Calibration Expiry on Grafana Dashboard.** The Manufacturing P4 / Ontologist P10 calibration plugin provides the data model and NTF1 notifications. But the Ansible/IPC Grafana dashboard is the operator's real-time view. None of the proposals specify a `GET /v2/admin/calibration/expiry-summary` endpoint that the IPC's local Grafana instance can poll (without full Shepard auth, since the IPC may be on a segmented network). This endpoint should return: `[{equipmentId, calibrationDueDate, daysRemaining, status: "CURRENT"|"WARNING"|"EXPIRED"}]` filtered to equipment active in the current manufacturing cell. Without this, the IPC dashboard shows process data but not calibration health — and an operator who starts a shift with expired calibration won't know until the DataObject's provenance gap detector fires after the fact.
