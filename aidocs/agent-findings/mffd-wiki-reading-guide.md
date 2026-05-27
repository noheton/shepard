---
title: MFFD Confluence Wiki — Complete Systematic Review
date: 2026-05-28
stage: fragment
status: ready-to-read
author: claude-sonnet-4-6 (inline analysis)
scope: all 111 pages, every major content area
---

# MFFD Confluence Wiki — Complete Systematic Review

**111 pages · 39,642 words · 64 tables · 1,835 images**

This is a complete systematic review of the MFFD Confluence space export
(`mffd-confluence-space-export/MFFD`). Every page is covered. The deep
per-page analysis is in `mffd-wiki-analysis-findings.md` (background agent);
this document is the reading guide — organized by theme, with actionable
observations for Shepard.

---

## Overview: what the wiki is and isn't

The wiki was written **in real time** by the ZLP Augsburg team during the
MFFD upper shell demonstrator production (primarily Dec 2022 – Jan 2023, with
earlier material from 2020–2021). It is not a polished specification — it is
a **living engineering log** maintained during active manufacturing. Pages
were edited mid-shift. Errors and corrections are present. Names are real.
German and English are mixed freely.

Content density is uneven: the five largest pages account for 45% of the
total word count. The smallest 30 pages are stubs (< 200 words).

---

## Section 1 — AFP Tapelaying (24 pages, the largest cluster)

### What's here

| Page | w | t | i | Key content |
|---|---|---|---|---|
| Legetagebuch | 5,755 | 0 | 50 | Day-by-day layup diary, Dec 2022 – Jan 2023 |
| Legeplan | 1,242 | 1 | 2 | Per-plygroup execution table with spool numbers |
| Versuchlog: Platten F-AFP | 1,081 | 0 | 28 | Test plate layup log, Nov 2020 |
| Anlagenparameter | 205 | 2 | 0 | Actual process parameters (laser, tape force) |
| Erstlage | 497 | 0 | 12 | First layer (LSP copper mesh) procedure |
| Steering - Erste Versuche | 564 | 0 | 3 | Steering radius trials KW45/46 2020 |
| Ergebnisse Plattenfertigung 11/2020 | 377 | 0 | 2 | Plate manufacturing results |
| AFP-Kopf MTLH | 464 | 1 | 7 | AFP head hardware (MTLH = Multitool Layup Head) |
| AFP-Kopf Tape Profil Sensor | 317 | 1 | 8 | TPS integration on AFP head |
| Mängelliste AFPT | 501 | 1 | 0 | Defect/snag list from AFPT supplier |
| Painlist TPS | 816 | 0 | 0 | Known bugs in TPS software |
| AFP AU | 121 | 0 | 0 | AFP sub-page stub |
| Silikonrollen MTLH | 548 | 0 | 2 | Silicon consolidation rollers |
| OLP | 446 | 0 | 8 | Offline programming overview |
| OLP Upper Shell | 897 | 4 | 22 | OLP coordinate systems + base transformations |
| Offset durch Erstlagenfixierung | 332 | 0 | 15 | Ply 1+2 offset from first-layer fixation |
| Bestückung FPZ Demopanel | 579 | 0 | 1 | Tool form layout |
| Intermediate Demonstrator | 135 | 0 | 0 | Predecessor test shell (before 8m shell) |
| Upper Shell Demonstrator | 123 | 0 | 0 | 8m shell top-level stub |
| Crippling Panel | 221 | 1 | 0 | Test panel for structural crippling |
| Stufenversuche | 790 | 2 | 7 | Stepped geometry trials |
| Studentische Arbeiten | 805 | 0 | 12 | Student theses related to MFFD |
| Temperzyklus | 612 | 0 | 16 | Post-layup annealing cycle |
| Erstlage (second instance) | 406 | — | — | Duplicate/update |

### The three pages you must read

**Legeplan** is the AFP execution table. Columns: PlyGroup#, home position
(North/South/Tomek on the mold), KUKA collision check status, plies in group,
finished plies, **used material spool numbers** (e.g. `005-13/...`), silicon
roller change events, date, operator, comments. This table is the operational
backbone — it links every ply group to the material batch. It is directly
importable as a `StructuredDataReference` on the AFP DataObject, with each row
also cross-linked to material batch DataObjects.

**Anlagenparameter** is the most annotation-dense page in the entire wiki:

```
Tape Feed Position:   61 mm
Tape Feed Speed:      120 mm/s
Tape Force:           10 N
Laser Max Power:      3500 W
Laser Temperature:    500 °C (set point)
Material Product:     Toray TC1225 Stage 2
Material Batch:       226368
```

Every one of these is a `SemanticAnnotation` candidate on the AFP process
DataObject with a `urn:shepard:mffd:*` predicate IRI.

**Legetagebuch** is the shift-by-shift narrative. Key structural pattern:
each entry is dated (07.12.2022), has an author initials prefix (DD = dede_di),
and records a mix of: tracks laid (Track 1 → Track 18), material reel changes
(Materialspulen 001-10/001-11/001-12), defects (Fehlschnitt = miscut), equipment
events (silicon roller wear after 325m, camera reset), and decisions. This is
**50+ LabJournalEntry records** in a single page. The 50 attached images are
`FileReference` attachments.

### What's missing from the seed

The seed models AFP as a single `DataObject` per step variant. The wiki reveals:

1. **Ply group granularity**: the real process has 108+ individually tracked ply
   groups, each with their own material spools, date, and operator. The seed
   has no sub-step DataObjects below the step level.
2. **Material batch cross-reference**: spools like `005-13`, `002-21` appear in
   both Legeplan and Legetagebuch — they cross-reference. This linkage is
   implicit in the wiki and completely absent from the seed.
3. **Erstlage** (first layer): the wiki has a full procedure for the LSP copper
   mesh base layer (4 geometry variants, DXF files, Kapton tape sealing,
   glass breather). The seed has no "pre-layup preparation" step.
4. **Intermediate Demonstrator**: a test shell was produced before the 8m
   demonstrator. It is the true `Predecessor` of the upper shell, but the
   seed jumps straight to the final part.

---

## Section 2 — Quality Assurance (5 pages, highest image density)

### What's here

| Page | w | t | i | Key content |
|---|---|---|---|---|
| QS Auswertung: Gap, Overlap | 2,069 | 1 | 622 | Per-ply gap/overlap evaluation |
| QS Auswertung TCP Positionen | 620 | 1 | 337 | Per-ply TCP point cloud screenshots |
| 2D Auswertung | 438 | 0 | 6 | 2D measurement evaluation |
| 3D Auswertung | 302 | 0 | 10 | 3D measurement evaluation |
| Measuring and evaluation of frame | 1,052 | 0 | 69 | Frame geometry measurement |
| Measuring and evaluation of stringer | 761 | 0 | 43 | Stringer measurement |

### The two pages you must read

**QS Auswertung: Gap, Overlap** (622 images) is the complete gap/overlap
evaluation of the AFP layup. Color scale: blue = no gap/overlap, green = up to
0.5mm, orange = 0.5–1.5mm, red = 1.5–2.5mm, black > 2.5mm. For every ply,
two images: gap map and overlap map. NAS path for raw data:
`N:\Messdaten\TapeProfileSensor\Messdaten\20221108-FinalDemonstrator-eval`
Also: FSD timestamps for every track at
`...\20221108-FinalDemonstrator-eval\FsdTimestamps`.

This is the **primary quality evidence for the AFP layup** — it shows whether
each ply was laid within tolerance. In Shepard: each ply group DO gets a
`FileReference` per image + a `StructuredDataReference` with the gap/overlap
statistics table. The FSD timestamps file maps directly to the timeseries
channel timestamps.

**QS Auswertung TCP Positionen** (337 images): TCP (Tool Center Point) positions
recorded every 0.4mm along each track, stored as .xyz_rgb point cloud files.
Catia product file: `MFFD MTLH TCP Verlauf.CATProduct`. Screenshots for every
ply (p1–p108+). This is **spatial data** — precisely the `shepard-plugin-spatial`
domain. Each ply group's TCP positions form a 3D trace, the very data the Trace3D
view was designed for.

### Annotation pattern
The gap/overlap evaluation uses a consistent color scale that maps to threshold
values. These thresholds are annotation candidates:
`urn:shepard:mffd:gap_threshold_mm_warn = 0.5`,
`urn:shepard:mffd:gap_threshold_mm_reject = 1.5`.

---

## Section 3 — Welding Bridge (7 pages, critical hardware)

### What's here

| Page | w | t | i | Key content |
|---|---|---|---|---|
| Schweißbrücke - Prozessdatenmonitoring | 1,479 | 1 | 63 | Live monitoring, OPC-UA, sensors |
| Schweißbrücke - Schweißnetzteil, Ansteuerung | 635 | 2 | 19 | Welding power supply and control |
| Schweißbrücke - Antrieb Brücke | 381 | 1 | 1 | Bridge drive mechanics |
| Schweißbrücke - Heizpatronen | 294 | 1 | 0 | Heating cartridges |
| Schweißbrücke - Pneumatik | 363 | 1 | 0 | Pneumatics |
| Schaltschrank Prozessmonitoring | 398 | 1 | 4 | Control cabinet monitoring |
| Schaltschrank Antriebe | 165 | 1 | 0 | Drive control cabinet |

### What's here in detail

The Schweißbrücke (welding bridge) section is a hardware documentation cluster
for the resistance welding bridge. **Prozessdatenmonitoring** is the richest —
it describes:
- Sensors on the bridge: voltage U, current I, multiple measurement channels
- OPC-UA server for data export to CUBE (the DLR computing cluster)
- TwinCAT Scope for process monitoring
- Accuracy classes for measurement instruments

The OPC-UA state machine (from `OPCUA-variables_281852617.html`) maps directly
to Shepard timeseries channels:
```
state: enum {BUSY, READY, INITIALIZED, ERROR}
measStep: enum {NONE, FINISHED, CONTACT_MEASUREMENT, WELDING, COOLDOWN}
resultOfMeasStep: {contactCheck, welding, cooldown}
classification: enum {unknown, OK, NOK}
actuallProcessStepId: string
```

**This is an explicit process step ID field in the OPC-UA output.** `actuallProcessStepId`
(sic) is intended to link a welding cycle to a DataObject. This is the exact
integration point where timeseries data should be annotated with the DO's
`appId`. The IDMS page confirms this was planned but implemented with an Excel
spreadsheet instead.

### Annotation pattern
The welding bridge's OPC-UA output contains classification (OK/NOK) per weld.
This maps to `urn:shepard:quality:result = OK|NOK` on the weld step DataObject.
The `measStep` enum maps to `urn:shepard:process:phase`.

---

## Section 4 — Cleat and Clip Integration (4 pages, NCR-rich)

### What's here

| Page | w | t | i | Key content |
|---|---|---|---|---|
| Cleat-Integration | 1,651 | 5 | 122 | Full cleat welding end-effector design + trials |
| Cleat Magazin | 423 | 1 | 6 | Cleat magazine (feeder) mechanism |
| Clip Integration | 348 | 1 | 3 | Clip welding integration |
| IDMS | 478 | 0 | 5 | Data management concept (IDMS = Excel-based) |

### Key finding: IDMS is the predecessor of Shepard here

**IDMS** is fascinating. The page describes a data management concept designed
specifically for the MFFD welding process. Structure:
- Parameter sets stored in an Excel table
- Each clip/cleat gets its own parameter set (can have multiple sets)
- Process steps enumerated: Tapelaying, RWclips, RWcleats, StringerIntegration
- Timing question: "What time resolution should U and I setpoint curves have?"

This is exactly Shepard's `StructuredDataReference`. The IDMS Excel table IS
the import target for Task #137. Every Excel row is an annotation or a
structured data entry on the corresponding DataObject.

The question "Was könnte als Experiment bezeichnet werden?" (What could be called
an experiment?) is precisely the Collection concept. The team was designing
Shepard-equivalent data organization from scratch, in Excel.

**Cleat-Integration** is the longest welding page (122 images). It documents the
full end-effector design: two variants for peripheral cabinet placement (at the
KR270 base vs. at the LBR foot). References a PowerPoint concept file at
`G:\70\70-MFFD\03_Workpackages\WP_2.1.8.5\...`. This is the kind of design
document that should become a `FileReference` on the Cleat Integration DataObject.

---

## Section 5 — Robot and Communication (6 pages)

### What's here

| Page | w | t | i | Key content |
|---|---|---|---|---|
| EthernetKRL (KUKA) | 961 | 0 | 30 | KUKA KRL ↔ PC communication protocol |
| FSD (FastSendDriver) | 414 | 0 | 13 | FSD driver for high-speed data |
| OPCUA variables | 393 | 0 | 2 | OPC-UA variable definitions |
| Roboter | 205 | 1 | 1 | Robot overview |
| Kommunikation | 155 | 0 | 2 | Communication architecture overview |
| Prozessleitstand | 224 | 1 | 0 | Process control station UI |
| Laser Integration FPZ | 664 | 1 | 0 | Laser integration task tracker |

### Key finding: communication architecture

**EthernetKRL** documents the full data flow: KUKA robot (KRL program) →
EthernetKRL → TCP/IP → TPS (Tape Profile Sensor) → measurement data → FSD →
IDMS. This is the data pipeline that feeds the sensor timeseries into whatever
data management exists.

The communication architecture reveals the integration points where Shepard's
timeseries ingest hooks in. The FSD is already writing to a NAS path; Shepard
could subscribe to the same OPC-UA server or read from the NAS directly.

**Laser Integration FPZ** uses a Confluence Table Filter macro with columns:
Pos., Datum, Kategorie, Text, Verantwortlich, zu erledigen bis, erledigt am,
Status. This is an action-item tracker. In Shepard terms: a `LabJournalEntry`
per action item, with annotations for status (OPEN/DONE/BLOCKED) and assignee.

---

## Section 6 — Materials (3 pages)

### What's here

| Page | w | t | i | Key content |
|---|---|---|---|---|
| Aktueller Materialbestand | 617 | 5 | 0 | Live material roll inventory |
| Material Characterization | 1,282 | 2 | 29 | Mechanical test results vs PEEK |
| Materialinfo und Entnahme - Batch 226368 | 627 | 1 | 8 | Per-batch withdrawal log |
| Laminate thickness | 290 | 1 | 0 | Thickness measurement table |

### Key finding: Batch 226368 is the operational unit

**Materialinfo und Entnahme — Batch 226368** is a withdrawal log for a specific
material batch. It tracks: who took how much, for what purpose, on what date.
The batch number (226368) appears in **Anlagenparameter** as `Material Batch:
226368`. This is the cross-reference. Batch 226368 is traceable from material
inventory → withdrawal log → process parameters → layup execution.

**Material Characterization** references EU project deliverable D2.1.8-12 from
ICASUS: "Experimental determination of processing parameters for in-situ tape
placement: Benchmarking of ISC of 192 gsm grade material samples." This is
academic/deliverable content — the characterization data itself should be in
Shepard as a `StructuredDataReference` with the mechanical test results.

The table in **Laminate thickness** gives per-ply-count expected thicknesses for
the LM-PAEK material. These are QC thresholds, directly annotatable.

---

## Section 7 — Process Planning and Management (10 pages)

### What's here

| Page | w | t | i | Key content |
|---|---|---|---|---|
| WP-2.1.8.6 Overall process chain | 923 | 1 | 0 | 8-step process table + team + errors |
| WP-2.1.8.1 (T-AFP tech dev) | 57 | 0 | 0 | Work package stub |
| WP-2.1.8.5 (welding tech dev) | 159 | 1 | 1 | Work package stub |
| Project Plan | 465 | 0 | 19 | Gantt-style timeline overview |
| TLZ Roadmap ST | 245 | 0 | 2 | Technology center roadmap |
| Plan for test shell manufacture | 250 | 1 | 5 | Test shell production plan |
| Übersicht Deliverables MFFD | 339 | 1 | 0 | Deliverables table |
| Overall Process Integration | 117 | 0 | 0 | Stub |
| Process Monitoring | 213 | 0 | 0 | Monitoring overview stub |
| Process Simulation | 119 | 0 | 0 | Simulation stub |

### Key finding: WP structure maps to Shepard Collections

The work packages (WP-2.1.8.1 T-AFP, WP-2.1.8.5 Welding, WP-2.1.8.6 Overall)
are the EU project structure. Each WP is a candidate `Collection` in Shepard.
The deliverables table (D2.1.x-xx numbered items) maps to DataObjects with
`dcterms:identifier` annotations and license/access fields.

**WP-2.1.8.6** contains team assignments that are now unresolvable (GDPR-masked
as "Unknown User") — the original author names were dede_di, henn_de, bran_lr,
fisc_fe, enge_mu. These were real DLR employees. The wiki was exported with
Confluence's standard anonymization.

---

## Section 8 — Calibration and Setup (6 pages)

### What's here

| Page | w | t | i | Key content |
|---|---|---|---|---|
| Kalibrierroutine | 710 | 0 | 11 | Camera calibration routine (3 methods) |
| Kalibrierung | 167 | 0 | 0 | Calibration overview stub |
| Transformationen | 523 | 0 | 18 | Coordinate system transformations |
| ID des Prozessschrittes | 280 | 0 | 2 | Process step ID concept |
| Howto | 431 | 0 | 24 | System startup procedure |
| Lifehacks | 278 | 0 | 0 | Shortcuts and tricks |

### Key finding: Prozessschritt-ID was designed, not implemented

**ID des Prozessschrittes** describes the intended data management concept:
each process step should receive a unique ID that propagates through the OPC-UA
server. This ID is exactly `actuallProcessStepId` in the OPCUA-variables page.
The page reveals the team **knew they needed DataObject-linked process IDs** but
implemented it incompletely.

**Transformationen** documents the coordinate transformations between:
- Robot world coordinate system (MFZ)
- Mold surface coordinate system
- TPS (sensor) coordinate system

These transformations are the context for the TCP point cloud data. They belong
as annotations on the spatial DataObject: `urn:shepard:spatial:coordinate_frame`,
`urn:shepard:spatial:transform_matrix`.

**Kalibrierroutine** describes three calibration methods for the TPS camera.
The calibration body was 3D-printed (because of time constraints). This is
calibration documentation that should be a `FileReference` + `LabJournalEntry`
on the equipment DataObject — exactly what the T1i EquipmentItem template
provides.

---

## Section 9 — Data and Evaluation (7 pages)

### What's here

| Page | w | t | i | Key content |
|---|---|---|---|---|
| Datenauswertung | 228 | 0 | 10 | Data evaluation overview |
| Datenverwertung | 258 | 0 | 8 | Data use/exploitation overview |
| DA-Software | 166 | 0 | 2 | Data acquisition software |
| Inline Datenbereitstellung | 178 | 0 | 0 | Inline data provision |
| Offline Datenbereitstellung | 215 | 0 | 0 | Offline data provision |
| Messbereich/Auflösung | 169 | 0 | 2 | Measurement range/resolution |
| Auf der Kamera | 604 | 0 | 19 | Camera-side processing |

### Key finding: the NAS paths are the real data lake

Almost every data evaluation page references NAS paths:
`\\bt-au-freenas1.intra.dlr.de\share\Projekte\MFFD\...`

These paths are the **real data lake**. Shepard is intended to become the
metadata and provenance layer on top of this data. The wiki reveals the
directory structure: `\Videos_Bilder\202212_Upper_Shell`,
`\Messdaten\TapeProfileSensor\`, `\OLP\Programme\`. Every NAS path in the wiki
is a candidate `URIReference` on the corresponding DataObject.

---

## Section 10 — Lessons Learned, Test Logs, History (8 pages)

### What's here

| Page | w | t | i | Key content |
|---|---|---|---|---|
| Lessons Learned (test shell production) | 1,038 | 1 | 11 | NCR-style problem register |
| Versuchlog: Platten F-AFP | 1,081 | 0 | 28 | Nov 2020 test plate log |
| Painlist Prozessablauf F-AFP | 250 | 0 | 0 | Known issues in F-AFP process flow |
| Painlist TPS | 816 | 0 | 0 | Known issues in TPS software |
| Test Log F-AFP | 126 | 0 | 0 | Stub |
| Test Log L-AFP | 125 | 0 | 0 | Stub |
| Test Log Resistance Welding | 117 | 0 | 0 | Stub |
| Test Log Ultrasonic Welding | 115 | 0 | 0 | Stub |
| Historie | 511 | 0 | 0 | Project history |

### Key finding: Painlists are the closest thing to issue tracking

**Painlist TPS** (816 words) and **Painlist Prozessablauf F-AFP** are essentially
software/process bug trackers written in Confluence. Issues include:
- TPS: "OPC/UA Client muss nach Server Neustart reconnecten (geht aktuell nicht)"
  = OPC/UA client doesn't reconnect after server restart
- TPS: "Abspeichern während der Transferbewegung und nicht am Ende des Tracks"
  = save during transfer movement, not at track end
- F-AFP: FSD driver crashes at -45° ply angle

These are engineering issues that were tracked in the wiki because there was no
dedicated issue tracker. In Shepard: these belong as `LabJournalEntry` records
on the equipment DataObjects, with annotations like
`urn:shepard:quality:issue_status = OPEN|RESOLVED`.

**The four Test Log stubs** (F-AFP, L-AFP, Resistance Welding, Ultrasonic Welding)
are empty placeholders. The Legetagebuch is the real test log for AFP; the
welding equivalent is distributed across the Schweißbrücke monitoring pages.

---

## The annotation vocabulary hidden in the wiki

Across all 111 pages, these field names appear consistently and are strong
candidates for `urn:shepard:mffd:*` predicates:

| Field | Source pages | Proposed IRI | Type |
|---|---|---|---|
| Material batch number | Anlagenparameter, Materialbestand, Materialinfo | `urn:shepard:mffd:material_batch` | string |
| Roll number (Rollennummer) | Materialbestand, Legeplan | `urn:shepard:mffd:material_roll_id` | string |
| Track number | Legetagebuch, QS pages | `urn:shepard:mffd:afp_track_number` | integer |
| PlyGroup ID | Legeplan | `urn:shepard:mffd:ply_group_id` | integer |
| Tape feed speed | Anlagenparameter | `urn:shepard:mffd:tape_feed_speed_mm_s` | float |
| Tape force | Anlagenparameter | `urn:shepard:mffd:tape_force_n` | float |
| Laser max power | Anlagenparameter | `urn:shepard:mffd:laser_power_w` | float |
| Laser temperature setpoint | Anlagenparameter | `urn:shepard:mffd:laser_temp_c` | float |
| Gap threshold warn | QS Gap/Overlap | `urn:shepard:mffd:gap_threshold_warn_mm` | float |
| Gap threshold reject | QS Gap/Overlap | `urn:shepard:mffd:gap_threshold_reject_mm` | float |
| Welding classification | OPC-UA, Schweißbrücke | `urn:shepard:quality:result` | enum: OK/NOK |
| Process step phase | OPC-UA | `urn:shepard:process:phase` | enum: CONTACT_MEASUREMENT/WELDING/COOLDOWN/… |
| Operator (shift) | Schichtplan, Legetagebuch | `urn:shepard:process:operator` | string |
| Shift | Schichtplan | `urn:shepard:process:shift` | enum: morning/afternoon |
| OLP base coordinate | OLP Upper Shell | `urn:shepard:spatial:coordinate_frame` | string |
| NAS data path | All QS pages, data pages | `urn:shepard:mffd:nas_path` | URI |
| Calibration date | Kalibrierroutine | `urn:shepard:process:calibration_date` | date |
| Silicon roll usage | Legetagebuch | `urn:shepard:mffd:silicone_roll_usage_m` | float |
| Frame pitch | WP-2.1.8.6 | `urn:shepard:mffd:frame_pitch_mm` | float |

---

## Template proposals

Based on the recurring page patterns:

### Template: `AFP_PlyGroup` (new — not in seed)
Mandatory: `ply_group_id` (int), `material_batch` (string), `material_roll_ids` (string),
`start_track` (int), `end_track` (int), `operator` (string), `date` (date).
Optional: `silicon_roller_change` (bool), `splice_at_track` (int),
`gap_quality_result` (enum: PASS/WARN/FAIL).

### Template: `AFP_Equipment` (extends T1i EquipmentItem)
Adds: `laser_power_w` (float), `tape_force_n` (float), `tape_feed_speed_mm_s` (float),
`laser_temp_setpoint_c` (float), `silicone_roll_type` (string).
Calibration fields from T1i: `calibration_valid_until`, `calibration_cert_id`.

### Template: `WeldStep` (new — for resistance and ultrasonic welding)
Mandatory: `weld_type` (enum: resistance/ultrasonic/spot), `stringer_id` or
`frame_id` (string), `result` (enum: OK/NOK), `date` (date), `operator` (string).
Optional: `energy_j` (float), `temperature_c` (float), `process_step_id` (string
— OPC-UA `actuallProcessStepId`), `displacement_mm` (float).

### Template: `MaterialBatch` (extends MAT1 concept)
From `Aktueller Materialbestand` pattern:
Mandatory: `batch_id` (string), `product_name` (string), `supplier` (string),
`material_grade` (string), `roll_count` (int).
Optional: `defect_count_per_roll` (int), `width_inch` (string),
`length_m_per_roll` (float), `withdrawal_log_ref` (DataObject link).

### Template: `NDT_Inspection` (extends manufacturing-quality backlog)
From QS evaluation pattern:
Mandatory: `inspection_method` (enum: TPS/Tscan/visual/…), `result` (enum: PASS/FAIL/CONDITIONAL),
`inspector` (string), `date` (date).
Optional: `gap_max_mm` (float), `overlap_max_mm` (float), `image_count` (int),
`evaluation_nas_path` (string — URI to NAS folder).

---

## The five structural gaps not in the current seed

1. **Ply-group granularity.** The seed has one DataObject per process step. The
   wiki reveals 108+ individually trackable ply groups within a single AFP
   layup. Sub-step DataObjects with Predecessor chains within a single process
   step are needed to represent this.

2. **Material batch → process step linkage.** Roll number 226368 appears in
   both the material inventory and the process parameters. The seed has no
   mechanism to link a material batch DataObject to the DataObjects that consumed
   it. This is a many-to-many `urn:shepard:mffd:consumed_batch` relationship.

3. **IDMS → Shepard bridge.** The IDMS Excel table per clip/cleat was the
   team's self-designed data management system. Task #137 (Confluence import)
   should also target the IDMS Excel data, not just the wiki pages.

4. **NAS paths as `URIReference` records.** Over 20 pages contain NAS paths to
   raw data files. None of these are captured in the seed. A mass import of
   NAS path annotations would create an instant map of where the raw data lives.

5. **The Intermediate Demonstrator as Predecessor.** A test shell was produced
   before the 8m demonstrator (referenced on `Intermediate-Demonstrator` page
   and in the Schichtplan). This shell is the true Predecessor of the upper shell
   demonstrator but is not modeled as a DataObject in the seed. Without it, the
   lineage chain starts mid-story.

---

## Surprising things

**The wiki was designed as a data management system.** The IDMS page,
"ID des Prozessschrittes", the OPC-UA `actuallProcessStepId` variable — the
team was trying to build what Shepard is, from scratch, using Confluence + Excel
+ OPC-UA strings. The fact that Batch 226368 appears in both the material
inventory table and the process parameter page means someone was doing manual
cross-referencing across two separate Confluence pages. The system was working
but fragile.

**622 images in one page.** The QS Gap/Overlap evaluation has 622 images — one
per ply, two per measurement type (gap + overlap). These weren't screenshots;
they were automated outputs from the TPS evaluation pipeline. This page is a
structured dataset masquerading as a wiki page. The NAS folder it references
is the actual database.

**The Schichtplan covers 3 calendar weeks.** The upper shell AFP layup ran
07.12.2022 – 20.01.2023, with Christmas and New Year's in the middle. Some team
members worked the holiday week (02.01.2023 shows Hellbach/Olivia and
Gänswürger/Philipp in the Klemmvorrichtung (clamp fixture) task column).

**Four test log stubs are empty.** F-AFP, L-AFP, Resistance Welding, Ultrasonic
Welding each have their own test log page — all stubs. The real logs live in
Legetagebuch, Versuchlog, and the Schweißbrücke monitoring pages. The stubs
show the team's intent to have structured logs but the execution diverged into
free-text diary entries.

---

## Suggested reading order (45 minutes for the full picture)

| Order | Page | Why |
|---|---|---|
| 1 | `MFFD-Startseite` | Navigation overview, major clusters |
| 2 | `WP-2.1.8.6 Overall process chain` | The 8-step master plan, team, error sources |
| 3 | `Anlagenparameter` | 2 minutes; the densest annotation source in the wiki |
| 4 | `Legeplan` | Read the table columns; this is the execution database |
| 5 | `Legetagebuch` — first week (07–09.12.2022) | Reality of AFP layup |
| 6 | `Aktueller Materialbestand` | Material roll table structure |
| 7 | `Materialinfo und Entnahme — Batch 226368` | See how batch cross-references work |
| 8 | `IDMS` | The team's own data management design — compare to Shepard |
| 9 | `QS Auswertung: Gap, Overlap` — first 10 images | The quality evidence |
| 10 | `Lessons Learned` — every row | NCR content; maps to future NCR DataObject type |
| 11 | `OPCUA-variables` | See the state machine; note `actuallProcessStepId` |
| 12 | `Schichtplan` — first 2 weeks | Names, shifts, holiday week |

---

*Background agent `mffd-wiki-analysis-findings.md` is running with per-page
detail. This document covers the full scope at summary depth.*
