---
stage: fragment
last-stage-change: 2026-05-28
audience: contributors, researchers, data stewards
---

# MFFD Confluence Wiki Analysis — Findings

**Source:** `/opt/shepard/examples/mffd-showcase/raw-data/mffd-data/mffd-confluence-space-export/MFFD/`
**Analysed:** 2026-05-28
**Analyst:** automated agent with manual page-by-page inspection

---

## What I found

### Summary statistics

| Metric | Value |
|---|---|
| Total HTML pages in export | 115 (excl. `index.html`) |
| Pages with substantive content (>200 words) | 73 |
| Pages that are near-empty stubs (<60 words) | 18 |
| Pages with at least one data table | 33 |
| Process steps mapped to seed.py DataObjects | 9 of 12 (3 welding-side steps undocumented) |
| Structural gaps not representable in current model | 8 distinct gap types |
| Candidate annotation predicates proposed | 22 |
| ShepardTemplate definitions proposed | 4 |

### Page clusters identified

| Cluster | Representative pages | Content type |
|---|---|---|
| AFP layup execution | Legeplan, Legetagebuch, Schichtplan, Materialinfo, Anlagenparameter, Erstlage, Silikonrollen-MTLH | Structured + diary |
| Inline QS (TPS) | QS Auswertung Gap/Overlap, QS Auswertung TCP Positionen, TPS Log, TPS (TapeProfileSensor), AFP Kopf—Tape Profil Sensor, Painlist TPS, Kalibrierung, Kalibrierroutine, Auf der Kamera | QS metrics + method |
| NDT / post-layup inspection | Datenauswertung, 2D Auswertung, 3D Auswertung, Ergebnisse, Laminate thickness, Material Characterization, Stufenversuche, Crippling Panel | Results + test norms |
| OLP / robot programming | OLP, OLP Upper Shell, Offline Datenbereitstellung, Inline Datenbereitstellung, DA-Software, ID des Prozessschrittes, EthernetKRL, FSD, CVB | Software method |
| AFP head hardware (MTLH) | AFP-Kopf—MTLH, AFP AU, L-AFP, Hardware MTLH, Laser Integration FPZ, Laserline LDM 6000-100, MTLH, Roboter, Mechanische Komponenten, Medienversorgung, Schaltschrank Antriebe, Schaltschrank Prozessmonitoring | Equipment specs |
| Welding bridge hardware | Schweißbrücke—Antrieb, Schweißbrücke—Schweißnetzteil, Schweißbrücke—Heizpatronen, Schweißbrücke—Pneumatik, Schweißbrücke—Prozessdatenmonitoring, Frame Coupling Integrationsbrücke, cUS Kopf, OPC-UA variables, Cleat-Integration, Cleat Magazin | Equipment specs + signals |
| humm3 subsystem | ORU / On-Robot-Unit humm3, Chiller humm3, Powerunit humm3, Schaltschrank Schweißmodulansteuerung | Equipment |
| Process chain coordination | IDMS, Overall Process Integration, WP 2.1.8.6, Ablauf Schweißungen, OPCUA variables, Prozessleitstand | Integration / protocol |
| Manufacturing history | Legetagebuch (diary), Versuchlog Platten F-AFP, Ergebnisse Plattenfertigung 11/2020, Lessons Learned, Mängelliste Abnahme AFPT, Painlist Prozessablauf F-AFP | NCR / lessons learned |
| Teardown / QS summary | QS Auswertung TCP Positionen, QS Auswertung Gap/Overlap, Measuring and evaluation of frame/stringer | Measurement results |
| Materials management | Materialinfo und Entnahme Batch 226368, Aktueller Materialbestand, Temperzyklus | Material tracking |
| Project admin | MFFD Startseite, WP 2.1.8.1, WP 2.1.8.5, WP 2.1.8.6, Übersicht Deliverables, Intermediate Demonstrator, Upper Shell Demonstrator, TLZ-Roadmap-ST, Plan for test shell, Project Plan, Studentische Arbeiten | Admin |
| Empty stubs | Test Log F-AFP, Test Log L-AFP, Test Log Resistance Welding, Test Log Ultrasonic Welding, Resistance Welding, Ultrasonic Welding, Overall Process Integration, Upper Shell Demonstrator, AFP AU, L-AFP, Hardware MTLH, Process Simulation, Process Monitoring, Kommunikation, Datenauswertung, DA-Software, Howto, Datenverwertung | Planned but never filled |

---

## Process skeleton mapping

### Current seed.py DataObjects vs. wiki pages

| seed.py DataObject | seed.py description | Primary wiki pages | Coverage |
|---|---|---|---|
| AFP Tapelaying S1 | AFP layup (AFPT MTLH / KUKA KR270) | Legeplan, Legetagebuch, Anlagenparameter, Schichtplan, Materialinfo (Batch 226368), Silikonrollen-MTLH, Erstlage, AFP-Kopf—MTLH, MTLH, Roboter, AFP-Kopf—Tape Profil Sensor | Rich — but 52 PlyGroups invisible as single DO |
| AFP Tapelaying S2 | Parallel AFP track | Same pages (Legeplan has S1+S2 split by North/South robot) | Same gap |
| NDT Inspection S1 (FAIL) | Active thermography | QS Auswertung Gap/Overlap (p1–p151), QS Auswertung TCP Positionen (151 plies), TPS Log, Kalibrierung | Inline TPS only; active thermography pages are empty stubs |
| Rework S1 | Repair pass | No dedicated wiki page found | Gap — rework is documented only in diary narrative, not a structured page |
| NDT Inspection S1 (PASS) | Re-inspection | No dedicated pass-state page found | Gap |
| AFP Tapelaying S2 | Second panel track | OLP Upper Shell has S2-specific OLP programs | Partial |
| NDT Inspection S2 (PASS) | | Same inline QS pages | Same gap as S1 |
| Stringer Welding S1 | cUS-W continuous ultrasonic | cUS-Kopf, Measuring and evaluation of stringer, Ultrasonic Welding (stub) | Thin — the stringer welding execution pages are stubs |
| Stringer Welding S2 | | Same | Stub |
| Frame Welding — Bridge | Resistance bridge welding (Brückenschweißen) | Schweißbrücke subsystem pages (5 pages), OPCUA variables, IDMS, Ablauf Schweißungen, Frame Coupling Integrationsbrücke, Lessons Learned | Equipment well-documented; execution diary absent |
| Frame Welding — Spot | Resistance spot welding (Punktschweißen) | Schweißbrücke subsystem, partial overlap with Bridge pages | Same |
| LBR Cleat Installation | KUKA LBR iiwa 14, Cleat Integration | Cleat-Integration, Cleat Magazin, Clip Integration, humm3 subsystem pages | Equipment well-documented; execution log absent |
| Stringerverbindung | Frame/stringer assembly join | WP 2.1.8.6, Overall Process Integration (stub) | Thin |

### The 52-PlyGroup gap (most important finding)

`seed.py` models AFP layup as **2 DataObjects** — one per panel track (S1, S2). The wiki's `Legeplan` page describes **52 PlyGroups (PG1–PG52)** as the actual unit of layup execution:

- Each PlyGroup has: Home robot position (North/South), RoboDK program name, plies in group, material spool numbers (3 spools per group, e.g. `001-10/001-11/001-12`), silicone roll change events, start date, finish date, and operator initials.
- `Materialinfo und Entnahme - Batch 226368` has 38 spool entries covering PG1–PG49, each with install date, operator, and quality comment.
- `Schichtplan` has 30 rows of daily shift assignments (Dec 7 2022 – Jan 20 2023) pairing date to individual operator names.
- `Legetagebuch` is a 5755-word narrative log covering Dec 7 2022 – Jan 20 2023 with track-level incidents: splice events, Fehlschnitt (cut failure), silicone roll changes, material exhaustion, laser failures, robot arm path issues.

**A faithful import would create 52 PlyGroup DataObjects as children of the AFP Tapelaying parent, not 2.** Each PlyGroup DO would carry:
- `LabJournalEntry`: extracted from Legetagebuch narrative (per-day text blocks correlated with PG dates)
- `StructuredDataReference`: one row of the Legeplan table per PlyGroup
- `SemanticAnnotation`: spool numbers, operator, silicone roll change flag

---

## Data associations per page cluster

### AFP Layup cluster

| Source page | Shepard target | Import notes |
|---|---|---|
| Legeplan (52 rows) | 52 StructuredDataReferences, one per PlyGroup; OR 52 child DataObjects | `--import-tables` flag; columns: PG#, Home, OLP check, Plies in group, Finished plies, Material (spools), Silicone roll change?, Date, Operator, Comments |
| Legetagebuch (5755 words) | LabJournalEntry on AFP DO (or split per-day LabJournalEntries on PlyGroup DOs) | HTML → markdown via html2text; per-day blocks correlate to PlyGroup dates |
| Schichtplan (30 rows) | StructuredDataReference (shift schedule) on AFP DO | Table: Day, Weekday, KW, Date, AM1, AM2, PM1, PM2, Absent, Comment |
| Materialinfo Batch 226368 (38 rows) | StructuredDataReference (spool log) on AFP DO, or SemanticAnnotations on PlyGroup DOs | Table: Spool numbers, Date, Installed by, Used for (PG#), Comment |
| Anlagenparameter | StructuredDataReference (one-row parameter snapshot) on AFP DO | 12 parameters: Layup Speed, Tape Feed Position, Tape Feed Speed, Tape Force Set Value, Laser Max Power, Laser Regler Set Temperatur, Laser Regler Vorsteuerung Offset, Laser Regler Vorsteuerung Faktor, Laser Regler Vorfilter Time, Laser Regler Temperatur Tn, Laser Height Set Pos, Material Product, Material Batch |
| Erstlage | LabJournalEntry on AFP DO | First-ply fixing procedure notes |
| Silikonrollen-MTLH | LabJournalEntry on AFP DO | Silicone roll degradation observations + change protocol |

### Inline QS / TPS cluster

| Source page | Shepard target | Import notes |
|---|---|---|
| QS Auswertung: Gap, Overlap (151 rows) | StructuredDataReference (per-ply QS table) on NDT DO; OR 151 child DataObjects | Per-ply: gap result, overlap result, flags (tow border width 0 or 3, Witteschlitten), dry-run incidents, point cloud generated flags |
| QS Auswertung TCP Positionen | LabJournalEntry on AFP DO; 151 FileReferences for CATIA screenshots (not in export ZIP) | 151 attachment links (images) reference NAS path `N:\Messdaten\TapeProfileSensor\...` |
| TPS Log | LabJournalEntry on AFP DO | Sparse log of TPS incidents |
| Kalibrierung / Kalibrierroutine | LabJournalEntry on Equipment DataObject (AFP head) | Calibration procedure + result |
| AFP Kopf—Tape Profil Sensor | LabJournalEntry + StructuredDataReference on AFP head Equipment DO | TPS sensor specs |
| Painlist TPS | LabJournalEntry on AFP DO | Known issues with TPS measurement reliability |

### NDT / Post-layup inspection cluster

| Source page | Shepard target | Import notes |
|---|---|---|
| Material Characterization | StructuredDataReference (test norms table) on NDT DO | Test norms: DIN EN 2563, DIN EN 2597 B, AITM 1-0007, AITM 1-0008 |
| Laminate thickness (table) | StructuredDataReference on NDT DO | Per-specimen thickness measurements |
| Stufenversuche | LabJournalEntry + StructuredDataReference on NDT DO | Step-specimen results |
| Crippling Panel | LabJournalEntry on NDT DO | Crippling test results |
| 2D Auswertung, 3D Auswertung | LabJournalEntry on NDT DO | TPS data evaluation methods |

### Welding / assembly cluster

| Source page | Shepard target | Import notes |
|---|---|---|
| OPCUA variables | StructuredDataReference (signal schema) on Frame Welding DO | OPC-UA variable definitions: state enum, measStep enum, classification OK/NOK, liveResultsOfMeasStep with units (Celsius, Volt, Ampere, Pascal, Ohm) |
| Schweißbrücke subsystem pages (5) | LabJournalEntries on Frame Welding Equipment DataObject | Function, control logic, communication interfaces (TCP/IP, OPC-UA) |
| Ablauf Schweißungen | LabJournalEntry on Frame Welding DO | Step-by-step welding sequence protocol |
| IDMS | LabJournalEntry on Frame Welding DO | Data management system design notes, parameter set structure, IDMS API |
| Lessons Learned | LabJournalEntry on Frame Welding DO (or root DO) | NCR-like table: Problem, Risk, Mitigation, Tasks |
| Cleat-Integration | LabJournalEntry + StructuredDataReference on LBR Cleat DO | 5-table integration procedure |
| Measuring and evaluation of frame/stringer | LabJournalEntry on Frame/Stringer Welding DOs | Measurement and evaluation protocol text |

### Materials management cluster

| Source page | Shepard target | Import notes |
|---|---|---|
| Aktueller Materialbestand (5 tables) | StructuredDataReference on AFP DO or Collection root | Current material stock tables |
| Temperzyklus | StructuredDataReference on AFP DO | Annealing cycle protocol for the tooling form |

### Equipment / hardware pages (~30 pages)

Most equipment pages follow a standard structure: Funktion (function description), Ablaufsteuerung (control logic), Kommunikation (communication interfaces with Eingangssignale/Ausgangssignale tables). These map naturally to:

- **DataObject (Equipment type)**: one per piece of equipment
- **LabJournalEntry**: Funktion + Ablaufsteuerung text
- **StructuredDataReference**: I/O signal tables

Equipment DataObjects identified in wiki (not in current seed.py):
AFP-Kopf MTLH, TPS sensor, Laserline LDM 6000-100, KUKA KR270 Roboter (Tomekk/Samy), cUS head, Schweißbrücke (5 subsystems), Frame Coupling Integrationsbrücke, humm3 ORU, humm3 Chiller, humm3 Powerunit, Cleat end-effector, LBR iiwa 14 R820, Werkzeugform (tooling), Prozessleitstand

---

## Missed structure

The following entity types or relationship patterns exist in the wiki but have no clean Shepard representation today.

### M1 — PlyGroup as first-class entity

The wiki tracks 52 PlyGroups with full provenance (spool numbers, operator, date, silicone roll state, OLP program, per-ply incidents). The current model has no concept between "AFP Tapelaying process step" and "individual ply." A PlyGroup is a natural child DataObject.

**Proposed shape:** `AFP Tapelaying DO → [PARENT_OF] → PlyGroup DO (PG1 ... PG52)` with StructuredDataReference (one Legeplan row) and optional LabJournalEntry (diary extract for that date range).

### M2 — Silicone roll as consumable entity

`Legetagebuch` and `Legeplan` track numbered silicone rolls (No.1 through No.17+) with exact track-position change events. Roll number, change position (track number), cause (degradation, contamination), and observation are consistently recorded. The current model has no consumable/material-instance entity.

**Proposed shape:** `SiliconeRoll` as a DataObject (type=consumable) with predecessor links between rolls in the same campaign. Or at minimum: SemanticAnnotation `urn:shepard:mffd:siliconeRoll_number` on the AFP DO.

### M3 — Material spool as first-class entity

Three tape spools are loaded simultaneously (e.g. `001-10/001-11/001-12`). Splice events, spool changes, and material quality observations (e.g. "Wesentlich besser als die Rollen davor von der Homogenität") are recorded per spool set. The spool set is the material-batch-instance.

**Proposed shape:** `MaterialSpool` DataObject with attributes `batchNumber`, `spoolNumbers` (set), `installedDate`, `installedBy`, `usedForPlyGroup`. Attached to AFP DO as child or via URIReference to batch cert on NAS.

### M4 — Incident / Fehlschnitt as sub-event

`Legetagebuch` records multiple named incident types: Fehlschnitt (cut failure), Splice (tape splice event), Materialwechsel (material change), Laser-failure, Dryrun (dry-run pass), track-repetitions. These have timestamps (track numbers), operator notes, and resolution actions. Currently they would be buried inside a single LabJournalEntry.

**Proposed shape:** Each incident could be a child LabJournalEntry on the PlyGroup DO. Or a typed SemanticAnnotation (annotation key = incident type, value = track number + description). A `LabJournalEntry` supports a `type` field today — extending it to carry a structured `incidentType` would suffice.

### M5 — Mängelliste (NCR) as structured quality record

`391894283.html` (Mängelliste Abnahme AFPT) contains 14 NCR-style rows: `Mängelbeschreibung` (defect description), `Handlung DLR` (DLR action), `Handlung AFPT` (supplier action), `Dringlichkeit` (priority: hoch/mittel/niedrig). This is an FMEA/NCR document that maps to EN 9100 non-conformance records.

**Proposed shape:** StructuredDataReference (NCR table) on the AFP DO or on the equipment DO. Alternatively, each row is a child DataObject with status `NCR_OPEN` or `NCR_RESOLVED` — but that requires new status vocabulary (currently: DRAFT, IN_REVIEW, READY, PUBLISHED, ARCHIVED — no quality-management states).

### M6 — Operator identity as a tracked attribute (not free text)

`Schichtplan` resolves operator initials to full names: DD = Dominik Deden (dede_di), DH = Dennis Henneberg? (henn_de), OH = Olivia Hellbach, MMa = Monika Mayer, LB = Lisa Bransfeld? (bran_lr), FF = presumably Fabian Fischer (fisc_fe), ST = Samuel Tröger, TV = Toni Vogel. 

Currently these appear as initials in Legeplan and spool log. A proper mapping would link these to ORCID-like researcher identities. The Schichtplan page provides the name↔initials mapping.

**Proposed shape:** SemanticAnnotation `urn:shepard:mffd:operator` on each PlyGroup DO with a resolved name or ORCID, rather than initials only.

### M7 — OPC-UA signal schema as structured metadata

`OPCUA-variables` defines a rich signal schema: `state` (BUSY/READY/INITIALIZED/ERROR), `measStep` (NONE/FINISHED/CONTACT_MEASUREMENT/WELDING/COOLDOWN), `resultOfMeasStep` with `classification` (OK/NOK), `liveResultsOfMeasStep` with typed units (Celsius, Volt, Ampere, Pascal, Ohm), `processStepId`. The `processStepId` is the IDMS handle connecting a welding process step to its data record.

**Proposed shape:** StructuredDataReference (signal schema JSON) on Frame Welding DO. The `processStepId` maps to `urn:shepard:mffd:processStepId` annotation on each welding result record. Timeseries channels from welding (resistance, voltage, current, temperature) attach via TimeseriesReference on the per-weld child DO.

### M8 — Process step as schedulable unit (IDMS concept)

The `IDMS` page (and `ID des Prozessschrittes`) describes an external system that assigns `processStepId` identifiers to each welding/layup execution. The IDMS was the predecessor data management system: each experiment (Tapelaying, RWclips, RWcleats, StringerIntegration) received a distinct ID. Shepard's import must either ingest these IDs as `urn:shepard:mffd:idmsId` annotations or map them to `appId` successor records.

**Proposed shape:** SemanticAnnotation `urn:shepard:mffd:idmsId` on each process-step DO, carrying the legacy IDMS identifier for cross-referencing with any surviving IDMS data exports.

---

## Annotation patterns

### Proposed predicate IRIs — AFP / layup domain

| Predicate IRI | Range | Source | CHAMEO / SSN cross-ref |
|---|---|---|---|
| `urn:shepard:mffd:plyGroup` | xsd:integer (1–52) | Legeplan, Materialinfo | — |
| `urn:shepard:mffd:plyId` | xsd:integer (1–151+) | Legeplan, Gap/Overlap QS | — |
| `urn:shepard:mffd:robotHome` | xsd:string ("North (Tomekk)" / "South (Samy)") | Legeplan | — |
| `urn:shepard:mffd:olpProgram` | xsd:string (OLP program filename) | Legeplan | — |
| `urn:shepard:mffd:olpChecked` | xsd:string ("OK" / "fail") | Legeplan | — |
| `urn:shepard:mffd:materialBatch` | xsd:string (batch number, e.g. "226368") | Anlagenparameter, Materialinfo | `urn:chameo:batchNumber` (CHAMEO 3.1) |
| `urn:shepard:mffd:materialSpools` | xsd:string (comma-sep spool numbers) | Legeplan, Materialinfo | — |
| `urn:shepard:mffd:siliconeRollNumber` | xsd:integer | Legeplan | — |
| `urn:shepard:mffd:siliconeRollChanged` | xsd:boolean | Legeplan | — |
| `urn:shepard:mffd:operator` | xsd:string (full name or ORCID URI) | Legeplan, Schichtplan, Materialinfo | `schema:performer` |
| `urn:shepard:mffd:layupSpeed_mmPerS` | xsd:decimal (125.0) | Anlagenparameter | `ssn-sosa:hasResult` + `qudt:MillimeterPerSecond` |
| `urn:shepard:mffd:laserMaxPower_W` | xsd:decimal (3500.0) | Anlagenparameter | `ssn-sosa:hasResult` + `qudt:Watt` |
| `urn:shepard:mffd:laserSetTemperature_degC` | xsd:decimal (500.0) | Anlagenparameter | `ssn-sosa:hasResult` + `qudt:DegreeCelsius` |
| `urn:shepard:mffd:tapeForce_N` | xsd:decimal (10.0) | Anlagenparameter | `ssn-sosa:hasResult` + `qudt:Newton` |

### Proposed predicate IRIs — QS / NDT domain

| Predicate IRI | Range | Source | CHAMEO / SSN cross-ref |
|---|---|---|---|
| `urn:shepard:mffd:gapWidth_mm` | xsd:decimal | QS Auswertung Gap/Overlap | `chameo:GapMeasurement` |
| `urn:shepard:mffd:overlapWidth_mm` | xsd:decimal | QS Auswertung Gap/Overlap | `chameo:OverlapMeasurement` |
| `urn:shepard:mffd:towBorderWidth` | xsd:integer (0 or 3) | QS Auswertung Gap/Overlap | — |
| `urn:shepard:mffd:dryRunFlag` | xsd:boolean | QS Auswertung Gap/Overlap, Legetagebuch | — |
| `urn:shepard:mffd:qsClassification` | xsd:string ("OK" / "NOK" / "unknown") | OPCUA variables, QS Auswertung | `chameo:ClassificationResult` |
| `urn:shepard:mffd:testNorm` | xsd:string (e.g. "DIN EN 2563") | Material Characterization | `dct:conformsTo` |

### Proposed predicate IRIs — Welding / process monitoring

| Predicate IRI | Range | Source | CHAMEO / SSN cross-ref |
|---|---|---|---|
| `urn:shepard:mffd:processStepId` | xsd:string (IDMS identifier) | IDMS, ID des Prozessschrittes, OPCUA variables | — |
| `urn:shepard:mffd:weldMeasStep` | xsd:string (NONE / CONTACT_MEASUREMENT / WELDING / COOLDOWN / FINISHED) | OPCUA variables | `ssn-sosa:Observation` procedure |
| `urn:shepard:mffd:weldClassification` | xsd:string ("OK" / "NOK" / "unknown") | OPCUA variables | `chameo:ClassificationResult` |
| `urn:shepard:mffd:consolidationPressure_Pa` | xsd:decimal | OPCUA liveResultsOfMeasStep | `ssn-sosa:hasResult` + `qudt:Pascal` |
| `urn:shepard:mffd:defectDescription` | xsd:string | Mängelliste, Lessons Learned | `dct:description` + `tern:FeatureOfInterest` |
| `urn:shepard:mffd:defectPriority` | xsd:string ("hoch" / "mittel" / "niedrig") | Mängelliste | — |
| `urn:shepard:mffd:idmsId` | xsd:string | IDMS, ID des Prozessschrittes | — |

### Notes on CHAMEO / SSN alignment

N1k (shipped 2026-05-27) confirms that CHAMEO + SSN/SOSA + IEC 61360/ECLASS bundles are now seeded in the Shepard semantic repository. The predicates above that cross-reference CHAMEO terms (`chameo:GapMeasurement`, `chameo:ClassificationResult`, `chameo:batchNumber`) can be validated against the seeded vocabulary immediately. The `ssn-sosa:hasResult` + QUDT unit pattern is the correct shape for physical measurements (speed, power, temperature, force) — not bare literal annotations.

---

## Template suggestions

### Template 1 — AFP Process Step (Tapelaying)

```json
{
  "templateId": "urn:shepard:mffd:template:afp-process-step",
  "displayName": "AFP Tapelaying Process Step",
  "targetEntity": "DataObject",
  "requiredAnnotations": [
    { "predicate": "urn:shepard:mffd:materialBatch",         "label": "Material batch number",          "required": true },
    { "predicate": "urn:shepard:mffd:materialSpools",        "label": "Tape spool numbers (3x)",        "required": true },
    { "predicate": "urn:shepard:mffd:operator",              "label": "Operator name / ORCID",          "required": true },
    { "predicate": "urn:shepard:mffd:robotHome",             "label": "Robot home position",            "required": true },
    { "predicate": "urn:shepard:mffd:olpProgram",            "label": "OLP program filename",           "required": false },
    { "predicate": "urn:shepard:mffd:olpChecked",            "label": "OLP program verified (OK/fail)", "required": true },
    { "predicate": "urn:shepard:mffd:siliconeRollNumber",    "label": "Silicone roll number",           "required": false },
    { "predicate": "urn:shepard:mffd:siliconeRollChanged",   "label": "Silicone roll changed this PG?", "required": false },
    { "predicate": "urn:shepard:mffd:layupSpeed_mmPerS",     "label": "Layup speed (mm/s)",             "required": true },
    { "predicate": "urn:shepard:mffd:laserMaxPower_W",       "label": "Laser max power (W)",            "required": true },
    { "predicate": "urn:shepard:mffd:laserSetTemperature_degC", "label": "Laser set temperature (°C)", "required": true },
    { "predicate": "urn:shepard:mffd:tapeForce_N",           "label": "Tape consolidation force (N)",   "required": true }
  ],
  "suggestedContainers": ["LabJournalEntry", "StructuredDataReference (Legeplan row)"]
}
```

**Key insight from wiki:** All 12 Anlagenparameter fields should be captured; they are currently aggregated into the AFP DO attributes dict as free text. With the template, a new AFP run would require confirming or changing each parameter individually.

### Template 2 — NDT Inspection Step

```json
{
  "templateId": "urn:shepard:mffd:template:ndt-inspection",
  "displayName": "NDT Inspection Step",
  "targetEntity": "DataObject",
  "requiredAnnotations": [
    { "predicate": "urn:shepard:mffd:qsClassification",   "label": "Overall QS result (OK/NOK)",    "required": true },
    { "predicate": "urn:shepard:mffd:testNorm",            "label": "Test norm(s) applied",          "required": false },
    { "predicate": "urn:shepard:mffd:operator",            "label": "QS operator",                   "required": true },
    { "predicate": "urn:chameo:instrumentType",            "label": "Measurement instrument type",   "required": true },
    { "predicate": "urn:shepard:mffd:gapWidth_mm",         "label": "Max gap width (mm)",            "required": false },
    { "predicate": "urn:shepard:mffd:overlapWidth_mm",     "label": "Max overlap width (mm)",        "required": false }
  ],
  "suggestedContainers": ["LabJournalEntry", "StructuredDataReference (per-ply QS table)", "FileReference (CATIA screenshots, TPS point clouds)"]
}
```

### Template 3 — Welding Process Step (Frame / Clip / Cleat)

```json
{
  "templateId": "urn:shepard:mffd:template:welding-step",
  "displayName": "Resistance Welding Process Step",
  "targetEntity": "DataObject",
  "requiredAnnotations": [
    { "predicate": "urn:shepard:mffd:processStepId",         "label": "IDMS process step ID",          "required": true },
    { "predicate": "urn:shepard:mffd:weldClassification",    "label": "Weld result (OK/NOK/unknown)",  "required": true },
    { "predicate": "urn:shepard:mffd:operator",              "label": "Operator",                      "required": true },
    { "predicate": "urn:shepard:mffd:weldMeasStep",          "label": "Process state at completion",   "required": false },
    { "predicate": "urn:shepard:mffd:consolidationPressure_Pa", "label": "Consolidation pressure (Pa)", "required": false }
  ],
  "suggestedContainers": ["TimeseriesReference (voltage, current, temperature traces)", "StructuredDataReference (welding parameters JSON per OPCUA-variables schema)"]
}
```

### Template 4 — Equipment Item

```json
{
  "templateId": "urn:shepard:mffd:template:equipment-item",
  "displayName": "Process Equipment Item",
  "targetEntity": "DataObject",
  "requiredAnnotations": [
    { "predicate": "dct:identifier",                    "label": "Equipment ID / serial",         "required": true },
    { "predicate": "dct:type",                          "label": "Equipment type",                "required": true },
    { "predicate": "dct:description",                   "label": "Function description",          "required": false },
    { "predicate": "urn:chameo:calibrationDate",        "label": "Last calibration date",         "required": false },
    { "predicate": "urn:chameo:instrumentType",         "label": "CHAMEO instrument type IRI",    "required": false },
    { "predicate": "schema:manufacturer",               "label": "Manufacturer",                  "required": false }
  ],
  "suggestedContainers": ["LabJournalEntry (Funktion + Ablaufsteuerung)", "StructuredDataReference (I/O signal table)", "FileReference (calibration certificate PDF)"]
}
```

---

## My own ideas

### Idea 1 — PlyGroup importer with diary correlation

The Legeplan table + Legetagebuch diary + Schichtplan + Materialinfo are four synchronized data streams covering the same 52-day layup campaign. An MFFD-specific importer (CF1b extension or standalone seed script) could:

1. Parse the Legeplan table to create 52 PlyGroup DataObjects as children of the AFP DO.
2. Assign each PlyGroup its date range from the `Date Start/Finish` columns.
3. Segment the Legetagebuch diary by date, correlating diary paragraphs to PlyGroups.
4. Attach spool data from Materialinfo as SemanticAnnotations on the matching PlyGroup DO.
5. Assign operator from Schichtplan by matching date to shift slot.

This would be the highest-fidelity representation achievable from the HTML export alone — without any NAS access.

### Idea 2 — Gap/Overlap QS as timeseries-like data, not just a table

The QS Auswertung pages cover 151 plies, each with gap + overlap classifications. Currently this would import as a single StructuredDataReference (table). A better model: each row is a per-ply observation that could be stored as a `:TimeseriesReference` where the "time axis" is ply index rather than timestamp. This would let a researcher query "give me all plies where gap classification = NOK" via the timeseries channel query surface, not full-text search.

The channel schema: `measurement=layup_qalitycheck, field=gap_class, value=0|3` (tow border width) with track-level annotation flags. This is exactly the `urn:shepard:mffd:plyGroup` semantic axis role pattern introduced by the TCP thermal trail for AFP DT1.

### Idea 3 — Equipment DataObject tree mirrors the physical machine hierarchy

The wiki organizes equipment hierarchically: Schweißbrücke has 5 subsystem pages (Antrieb, Schweißnetzteil, Heizpatronen, Pneumatik, Prozessdatenmonitoring). Each has an I/O signal table and OPC-UA endpoint. In Shepard, these would be a DataObject tree:

```
Frame Welding Station (DO)
  └─ Schweißbrücke Controller (DO)
       ├─ Antrieb Brücke (DO)          ← StructuredDataRef: I/O signals
       ├─ Schweißnetzteil (DO)         ← StructuredDataRef: I/O signals
       ├─ Heizpatronen (DO)
       ├─ Pneumatik (DO)
       └─ Prozessdatenmonitoring (DO)  ← StructuredDataRef: OPC-UA schema
```

This mirrors the `aidocs/platform/87-timeseries-appid-migration.md` "device as DO" model. Each welding TimeSeries channel would reference its parent equipment DO as its `deviceId`. The Schweißbrücke hierarchy would then be the queryable equipment tree for all welding experiments.

### Idea 4 — Mängelliste as the NCR datatype missing from Shepard

The Mängelliste page (and the Lessons Learned page) both have an identical NCR structure:

```
Problem description → Risk assessment → DLR action → Supplier action → Priority → Resolved?
```

This structure is universal across aerospace manufacturing and exactly what EN 9100 §8.7 (Control of Nonconforming Outputs) requires. Shepard should support it as a typed LabJournalEntry subtype or a StructuredDataReference schema, not just a plain text import. Proposing `urn:shepard:mffd:template:ncr` as a template that requires: `defectDescription`, `defectPriority`, `mitigationStrategy`, `responsiblePerson`, `dueDate`, `status` (open/resolved).

### Idea 5 — Confluence import as the MFFD digital thread bootstrap

The 112-page export is the entire **pre-Shepard knowledge layer** of the MFFD upper shell production. Importing it, combined with the real MFFD timeseries data (NAS paths identified in QS Auswertung pages), would create the first true **digital thread** — from design intent (OLP programs, machine parameters) through execution (Legetagebuch incidents) through QS (gap/overlap, TCP positions) through material traceability (spool numbers, batch certificates). The `confluence-analysis.md` pre-analysis shows 165 external HTTP links and 980 attachment links pointing to NAS paths. Each is an `unarchived_datasource` candidate. Surfacing these in the import report would give the MFFD team a concrete to-do list for completing the thread.

---

## Gaps and blockers

### G1 — Active thermography pages are empty stubs

The NDT pages `Datenauswertung`, `2D Auswertung`, `3D Auswertung` and both `Test Log` pages for ultrasonic and resistance welding are nearly empty (35–72 words, no tables, no content). The active thermography NDT execution log — the most important QS step after AFP layup — is not in this export. Either it was never written in Confluence, or it lives elsewhere (separate Confluence space, Excel, or in the NAS raw data folders referenced by the QS pages).

**Impact:** The NDT → FAIL → Rework → NDT PASS chain in `seed.py` is not backed by any wiki content. The QS Auswertung pages cover only inline TPS measurements, not post-layup active thermography results.

### G2 — Welding execution logs are absent

The `Ablauf Schweißungen` page and the Schweißbrücke subsystem pages describe the system design and signal schema but contain no execution log for any individual weld. There is no equivalent of the Legetagebuch for the welding process. The IDMS page suggests that welding execution data was stored in the IDMS system (Excel + OPC-UA) and is not replicated in Confluence.

**Impact:** The Frame Welding and Stringer Welding DataObjects in `seed.py` have no wiki backing for their execution history. These records would need to come from IDMS export or NAS data, not from this Confluence space.

### G3 — Attachment links without files (980 broken references)

The export ZIP does not contain the `attachments/` folder. 980 attachment links in page content reference files on the Confluence server (primarily images: CATIA screenshots, process photos, scan results). The QS Auswertung TCP Positionen page alone has 151 attachment image links, each a CATIA screenshot of a ply's TCP position. These are on the internal DLR wiki server and on the NAS.

**Impact:** Any import via CF1b will flag 980 FileReferences as unresolvable. The images would need to be obtained separately from the wiki server or NAS.

### G4 — NAS path references are rich but unreachable

Multiple pages reference data on internal DLR NAS paths:
- `\\bt-au-freenas1.intra.dlr.de\share\Projekte\MFFD\OLP\Programme\...` (OLP programs)
- `\\bt-au-freenas1.intra.dlr.de\share\Projekte\MFFD\Videos_Bilder\...` (videos + images)
- `N:\Messdaten\TapeProfileSensor\Messdaten\...` (TPS measurement data, CATIA files)

The `confluence-analysis.md` reports 0 NAS path URIReferences found by the existing scanner, but the NAS paths appear in page text (not as `<a href>` links). The existing link extractor only finds `<a href>` links; NAS paths embedded in `<p>` text (UNC paths like `\\host\share`) are missed.

**Recommended fix:** Extend the Confluence link extractor to scan `<p>` and `<li>` text for UNC patterns `\\host\share\path` and Unix absolute paths, flagging them as `unarchived_datasource=true` URIReferences.

### G5 — Operator identity is partially anonymised in export

The Confluence HTML export renders LDAP accounts as `Unknown User (dede_di)` for accounts that no longer exist (presumably departed team members). `Mayer, Monika` and other active accounts are preserved. The initials-to-name mapping can be recovered from the Schichtplan page (which lists full names), but it requires a manual cross-reference step.

**Recommended fix:** Before import, build a `confluence_user_map.csv` with columns `confluenceUserId, fullName, orcid` by reading the Schichtplan + Startseite pages. The CF2b "user mapping file" design in `aidocs/integrations/82-confluence-import.md` anticipates exactly this need.

### G6 — No status vocabulary for quality-management states

`seed.py` uses `DRAFT/IN_REVIEW/READY/PUBLISHED/ARCHIVED` as DataObject status values. The wiki's Mängelliste and Lessons Learned pages imply additional quality states: `NCR_OPEN`, `NCR_CLOSED`, `APPROVED`, `ON_HOLD`. These are standard EN 9100 §8.7 states.

**Impact:** A faithful MFFD import cannot represent the rework loop (NDT FAIL → rework → NDT PASS) using the current status vocabulary. Discussed further in `aidocs/agent-findings/manufacturing-quality.md`.

### G7 — IDMS is a predecessor system, not yet documented as migration source

The IDMS page describes a data management system (Excel + OPC-UA + custom software) that predates Shepard. Its data format is an Excel lookup table with parameter sets per clip/cleat. This is the authoritative source for welding execution data that is absent from Confluence.

**Recommended action:** An IDMS→Shepard importer (separate from CF1a/b) that reads the Excel parameter tables and creates welding-step DataObjects. This would be the `shepard-plugin-importer` library entry for MFFD-specific data.

### G8 — 52-PlyGroup granularity requires seed.py refactor

The current `seed.py` `DO_SPECS` dictionary has 2 AFP DOs. A faithful representation requires 52 PlyGroup child DOs. This is not a Shepard limitation — it is a seed script design decision. The demo narrative in `seed.py` uses the Q1 AFP anomaly (consolidation force drop + TCP temp spike at ply 5) which *does* align with PlyGroup granularity, but the coarse 2-DO structure obscures which ply triggered the anomaly.

**Recommended action:** Extend `seed.py` to create PlyGroup DOs dynamically from the Legeplan data, with the Q1 anomaly pinned to PG1 (plies 1–4, Dec 7–8 2022) or PG2 (ply 5 = Dec 8 2022, Track 56) based on the Legetagebuch diary entry for Dec 8 2022 where Track 56 required maintenance.
