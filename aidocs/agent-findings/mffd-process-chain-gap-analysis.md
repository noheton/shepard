---
stage: audited-by-personas
last-stage-change: 2026-05-26
---

# MFFD Synthetic Seed — Process Chain Gap Analysis

**Scope:** Validate `examples/mffd-showcase/data/generate.py` against published
literature and real ingested data from the MFFD Dropbox export.

**Primary sources:**
- Endraß et al. (2024), *On the Manufacturing and Assembly of the Multifunctional Fuselage Demonstrator's Upper Shell*, Polymertec 2024, eLib 209558
- Deden et al. (2023), *Full-Scale Application of in-situ AFP for the Production of a Fuselage Skin Segment*, SAMPE Europe 2023, eLib 199804
- Gardiner (2023), *Manufacturing the MFFD thermoplastic composite fuselage*, CompositesWorld, 26 Jun 2023
- Gardiner (2021), *Manufacturing the upper half of the MFFD*, CompositesWorld, 21 Jan 2021
- Mayer et al. (2023), *Quality Assured Aircraft Fuselage Production: Data Evaluation of a QC Sensor for Thermoplastic AFP*, FAIM 2023, d-nb.info/127256147X
- NLR STUNNING case (2023), nlr.org/newsroom/case/stunning
- Fraunhofer IFAM JEC Award press release (2025)
- Real MFFD frame-welding Dropbox export: `raw-data/mffd-data/mffd-framewelding/manifest.json` (3,371 DOs, 268 non-empty SD docs)
- Real MFFD tapelaying Dropbox export: `raw-data/mffd-data/mffd-tapelaying/` (~8,500 DOs, per-track attributes)

---

## 1. Process Chain Match

| Seed step name | Published terminology | Match / Gap |
|---|---|---|
| AFP Q1 / AFP Q2 panels | The upper shell is a **single 8 m skin** laid by one robot — no Q1/Q2 split in the upper shell. The *lower* shell (STUNNING/NLR) was **two 90° fuselage segments** co-consolidated in autoclave. | **MISMATCH — structural.** Q1/Q2 applies to the lower shell (NLR), not the upper shell (DLR/ZLP). DLR's upper shell is one in-situ AFP part. |
| Stringer welding (CRW) | Published as **continuous ultrasonic welding (cUS-W / CUW)**, not "continuous resistance welding (CRW)". 46 Z-stringers (upper shell), robot-based. | **WRONG acronym.** The seed uses CRW; the correct process is CUW or cUS-W. Resistance welding is used for frames, not stringers. |
| Frame spot welding ("punkt") / frame bridge welding ("bruecke") | A single process: resistance welding via the **"welding bridge"** (Schweißbrücke), a motor-driven 14-module tool that integrates C-frames sequentially along the frame foot. DLR has 24 C-frames. No separate "Punktschweißen" step is described. | **PARTIAL MISMATCH.** "Bruecke" is the correct term for the C-frame welding step. "Punkt" (spot) is not a named step — the bridge does sequential spot-like welds, but it is one process. |
| Assembly alignment / Stringerverbindung | The real final assembly step is **longitudinal seam welding** (CO₂ laser + ultrasonic) performed by Fraunhofer IFAM in Stade, joining upper and lower shells. No "Stringerverbindung" assembly step (joining the two halves of the upper shell) appears in the upper-shell process. | **GAP.** Longitudinal seam welding is performed at IFAM, not ZLP. The seed conflates Q1+Q2 upper-shell integration with the actual upper+lower shell marriage. |
| LBR iiwa cleat installation | **CONFIRMED.** DLR explicitly used "LBR iiwa cobot [KUKA Robotics, Augsburg]" as a cobot-on-robot for cleat resistance welding (Gardiner 2023, Endraß 2024). 42 cleats as shear ties between Z-stringers and C-frames. | **MATCH.** |
| NDT (active thermography) | **CONFIRMED.** The MFFD process includes NDT quality inspection. Active thermography is a known NDT method for thermoplastic CFRP; the tapelaying DO attributes confirm per-track QC data recording. | **MATCH (method plausible).** |

---

## 2. Instrument / Sensor Match

### AFP layup channels

| Seed channel | Published AFP parameter | Match / Gap |
|---|---|---|
| `tcp_temp_C` (Tool Centre Point temperature) | Real AFP literature uses **"nip-point temperature"** as the primary thermal control variable — the temperature at the consolidation roller contact point, measured by IR camera (Schiel et al. 2020; Deden et al. 2023). "TCP" (tool centre point) is a robot coordinate concept, not a thermal sensor name. ZLP's AFP machine records the nip-point temperature for closed-loop control. | **NAMING GAP.** The correct term is `nip_point_temp_C`. TCP temperature may exist as a robot-arm coordinate but is not the primary AFP thermal measurement. |
| `consolidation_force_N` | Confirmed: consolidation roller / nip-roll force is a key AFP parameter. Literature refers to "consolidation pressure" (in bar) and "force" (in N) depending on measurement point. | **MATCH.** |
| `layup_speed_mm_s` (120 mm/s nominal) | Confirmed: Deden et al. (2023) explicitly cite **125 mm/s** as the chosen layup speed for the full-scale MFFD skin ("a lay-up speed of 125 mm/s which yields an optimized ratio of productivity and mechanical performance"). Research range for LM-PAEK: 80–250 mm/s. | **MATCH — seed value 120 mm/s is close but below the published 125 mm/s.** Negligible discrepancy. |
| `head_temp_C` (215 °C nominal) | Laser-heated AFP; head body temperature is not the primary measurement (the laser spot / nip-point temperature is). Substrate temperature is recorded separately. | **PLAUSIBLE but off-label.** A "head temperature" sensor may exist for thermal management but is not the control variable. |
| `substrate_temp_C` (85 °C nominal) | Tool/substrate pre-heat: Schiel et al. (2020) studied tooling temperatures of 20–200 °C for LM-PAEK. 85 °C is below the "optimised" range (160–200 °C) cited in published parameter studies, though plausible as a pre-heat stage. | **LOW CONFIDENCE — value may be too cold.** Published studies suggest substrate/tooling temperatures of 160–200 °C for acceptable crystallinity. |
| `nip_pressure_bar` (6.5 bar nominal) | Confirmed: Schiel et al. (2020) used **6 bar** consolidation pressure explicitly. | **MATCH.** |

### Stringer welding channels (labelled CRW in seed)

The seed models `weld_current_A`, `weld_voltage_V`, `weld_force_N`, `weld_zone_temp_C`, `traverse_speed_mm_s` as resistance welding channels. **These are the correct channels for resistance welding (frame integration), but the seed assigns them to the stringer step.**

The stringer step in reality uses **continuous ultrasonic welding**, which records: **vibration amplitude, welding force, traverse speed, and weld quality energy** — not current/voltage. There is no weld current in ultrasonic welding.

**MISMATCH — channel set is for the wrong process.** The seed stringer channels describe resistance welding, not ultrasonic welding.

### Frame welding (bridge welding)

The seed models `spot_current_A`, `spot_voltage_V`, `spot_force_N`, `electrode_temp_C`, `displacement_mm`. The **real** MFFD bridge welding data (268 SD documents, SHOWCASE_ANALYSIS.md) contains 14 channels:

| Real channel | Semantics (from SHOWCASE_ANALYSIS.md) | Seed equivalent |
|---|---|---|
| `BridgePosition` | Motor-driven bridge linear position (mm) | MISSING |
| `CM_I` | Clamp current (A) | No equivalent |
| `CM_p` | Clamp pressure (bar) | Partially: `spot_force_N` |
| `CM_t` | Clamp time (s) | MISSING |
| `W1_I` | Weld head 1 current (A) | `spot_current_A` (single head only) |
| `W1_U` | Weld head 1 voltage (V) | `spot_voltage_V` |
| `W1_p` | Weld head 1 pressure (bar) | Partially: `spot_force_N` |
| `W1_t` | Weld head 1 time (s) | MISSING |
| `W2_I` | Weld head 2 current (A) | MISSING (no second head) |
| `W2_U` | Weld head 2 voltage (V) | MISSING |
| `W2_p` | Weld head 2 pressure (bar) | MISSING |
| `W2_t` | Weld head 2 time (s) | MISSING |
| `WC_I` | Weld counter current (A) | MISSING |
| `WC_t` | Weld counter time (s) | MISSING |

The real bridge welding tool has **two weld heads + clamp + counter** — a symmetrical dual-head arrangement. The seed models a single-head resistance welding step with an electrode temperature sensor (`electrode_temp_C`) which does not appear in the real 14-channel set. Real parameter values: W1_I ≈ 17–18 A, W1_U ≈ 19–25 V, W1_p ≈ 0.29–0.45 bar, CM_I = 5 A, CM_p = 3.5–4.9 bar (from SHOWCASE_ANALYSIS.md).

### Assembly channels

The seed models `alignment_dx_mm`, `alignment_dy_mm`, `clamp_force_kN`, `joint_temp_C`. This assembly step is not the longitudinal seam welding at IFAM (which is the real final assembly). There is no corresponding Shepard dataset from the real MFFD for this step. **Status: plausible fiction, not grounded in available data.**

### LBR iiwa channels

The seed models 7 joint angles (`j1_deg`–`j7_deg`) + 6-axis force-torque (`force_x/y/z_N`, `torque_x/y/z_Nm`). This matches the KUKA LBR iiwa 14 R820 specification (7-DOF, integrated F/T). DLR explicitly used "compliance-controlled steering capabilities" of the LBR iiwa for self-alignment during cleat resistance welding (Gardiner 2023). **MATCH — channel architecture correct.**

---

## 3. Parameter Name Accuracy

| Term | Assessment |
|---|---|
| `tcp_temp_C` | Misleading. "TCP" in AFP context = Tool Centre Point, a robot pose concept. The thermal sensor is "nip-point temperature" or "laser spot temperature". Rename to `nip_point_temp_C`. |
| `consolidation_force_N` | Correct terminology. |
| `nip_pressure_bar` | Correct, but "consolidation pressure" is more standard. Both terms appear in literature. |
| `weld_zone_temp_C` | For resistance welding (frames), the real parameter is implant temperature. "Weld zone temperature" is acceptable. |
| `BridgePosition` | CORRECT — this is the exact term from the real MFFD data. Seed does not include it. |
| `CM_I/p/t`, `W1_I/U/p/t` etc. | These are the real DLR column names from the bridge welding data (CM = clamp, W1/W2 = weld head 1/2, WC = weld counter). The seed's `spot_current_A` / `spot_voltage_V` are simplified English approximations. |
| "CRW" (continuous resistance welding) | Wrong for stringers. Stringer welding = **cUS-W / CUW** (continuous ultrasonic welding). Resistance welding = frame and cleat integration. |
| "Brückenschweißen" / "Punktschweißen" split | "Brückenschweißen" is correct for the C-frame step. "Punktschweißen" is not a separate named process step — the bridge does each foot sequentially (which resembles spot welding), but there is no separate "Punkt" step. |
| "Stringerverbindung" | This term does not appear in DLR publications. The joining of the two halves is "longitudinal seam welding" or "Längsnahtschweißen". |
| "AF_1"…"AF_N" (from real data) | "AF" = "Anschweißfuß" (weld foot), the per-frame-foot attachment sub-step. Each Frame N has 13 AFs (matching the 14-module bridge having 13 inter-module weld positions). Not modelled in the seed. |

---

## 4. Missing Steps

| Real process step | Present in seed? | Notes |
|---|---|---|
| LSP (Lightning Strike Protection) foil application before AFP | No | First process step, manual, full-scale tool surface |
| In-situ AFP quality monitoring (TPS laser triangulation sensor) | No | Gap/overlap detection per track, per Mayer et al. 2023 |
| Energy director application on stringer feet before cUS-W | No | Extra LM-PAEK resin layer on each stringer foot |
| C-frame co-consolidation / press forming (by Premium AEROTEC) | No | Incoming part production, not at ZLP |
| Z-stringer compression moulding (by Aernnova) | No | Incoming part production |
| Frame coupling integration (resistance welding, distinct from C-frame) | No | eLib 200851 mentions "frame-couplings" as a separate welded item |
| Cleat resistance welding (LBR iiwa) | YES | Correctly modelled |
| Demoulding and peel-ply removal after AFP | No | Labour-intensive step; skin released from Invar tool |
| Longitudinal seam welding (IFAM, Stade) | No | Final assembly step — CO₂ laser + ultrasonic, different location |
| Ultrasomic NDT / thermography NDT (in-process) | Partially | Seed has post-AFP NDT; real system has in-process TPS sensor during AFP |

---

## 5. Narrative Accuracy (Q1 anomaly → rework → recheck)

The anomaly narrative (AFP force drop + temperature spike at ply 5, delamination detected by active thermography, rework, recheck) is **structurally plausible** and consistent with known AFP failure modes (Deden 2023: "transient process" sensitive to material quality, compaction force excursions). However:

- The seed's anomaly is on "Q1" — but the upper shell is a single AFP part, not two panels. The "Q1/Q2" split does not exist in the upper shell.
- Active thermography NDT is a real method used for these parts.
- The rework procedure (abrasion, hand layup, consolidation press at 6 bar/160 °C) is consistent with thermoplastic composite rework practice.
- The real MFFD tapelaying data records per-track attributes (`run_number`, `track_number`, `silicone_roll_change`, `towborder_direction`), not a "panel" designation. An anomaly would be attached to a specific Track N (Run M).

**Verdict:** The anomaly mechanism is plausible; the panel topology that frames it (Q1/Q2) is fictional.

---

## 6. Confidence Scores

| Section | Confidence | Basis |
|---|---|---|
| AFP step exists, DLR/ZLP performs it | HIGH | Deden 2023, Mayer 2023, multiple CompositesWorld articles |
| AFP is a single upper-shell panel (not Q1/Q2) | HIGH | All sources; Q1/Q2 = STUNNING lower half (NLR) |
| Stringer welding = CUW, not CRW | HIGH | Every source consistently states "continuous ultrasonic welding" for stringers |
| Frame welding = resistance welding via "Schweißbrücke" | HIGH | Gardiner 2023, Endraß 2024, real data |
| Bridge welding 14-channel real data (CM/W1/W2/WC) | HIGH | SHOWCASE_ANALYSIS.md, real Dropbox export |
| LBR iiwa for cleat installation | HIGH | Gardiner 2023, Endraß 2024 explicit citation |
| Nip-point temperature (not "tcp_temp_C") as AFP control variable | HIGH | Schiel 2020, Deden 2023 |
| Layup speed ~125 mm/s for LM-PAEK | HIGH | Deden 2023: "125 mm/s" explicit |
| Nip pressure ~6 bar | HIGH | Schiel 2020: "6 bar" explicit |
| Stringer count (46 in upper shell) | HIGH | DLR press release, Endraß 2024 |
| Frame count (24 C-frames in upper shell) | HIGH | CompositesWorld, Endraß 2024 |
| "AF_N" as weld-foot granularity in real data | HIGH | Real Dropbox manifest (Frame 1…24 × AF_1…AF_13) |
| Substrate temperature 85 °C nominal | LOW | Seed invention; literature suggests 160–200 °C for good crystallinity |
| Assembly "Stringerverbindung" step and channels | LOW | Not found in literature; actual join = longitudinal seam welding at IFAM |
| Ultrasonic welding parameter channels (amplitude, energy) | MEDIUM | Real cUS-W parameters known from literature but not in real data export |

---

## 7. Recommended Seed Changes

Ordered by impact on seed credibility:

1. **CRITICAL — rename stringer welding step to cUS-W** and replace its channels (`weld_current_A` etc.) with ultrasonic welding channels: `sonotrode_amplitude_um`, `weld_force_N`, `traverse_speed_mm_s`, `weld_energy_J`, `sonotrode_contact_temp_C`. Current channels describe resistance welding, not ultrasonic.

2. **CRITICAL — remove Q1/Q2 panel topology from the upper shell.** DLR's AFP produces one 8 m upper-shell skin. If two-panel topology is desired for narrative interest, label it as a **lower shell (STUNNING-style)** with autoclave consolidation, or acknowledge the Q1/Q2 fiction explicitly. Alternatively, rename to "Segment A / Segment B" with a note that this is illustrative.

3. **SIGNIFICANT — rename `tcp_temp_C` to `nip_point_temp_C`** in AFP channels. Optionally retain a `head_laser_power_W` channel (which is the actual AFP control output).

4. **SIGNIFICANT — replace frame welding channel set** (`spot_current_A`, `spot_voltage_V`, `spot_force_N`, `electrode_temp_C`, `displacement_mm`) with the real 14-channel set: `BridgePosition`, `CM_I`, `CM_p`, `CM_t`, `W1_I`, `W1_U`, `W1_p`, `W1_t`, `W2_I`, `W2_U`, `W2_p`, `W2_t`, `WC_I`, `WC_t`. Real parameter ranges are documented in SHOWCASE_ANALYSIS.md.

5. **SIGNIFICANT — raise substrate_temp_C nominal** from 85 °C to ~160 °C to align with published LM-PAEK process windows (Schiel 2020: tooling temperature optimum 160–200 °C for crystallinity).

6. **MINOR — rename the stringer step acronym** in comments/docstrings from "CRW" (continuous resistance welding) to "cUS-W" or "CUW" (continuous ultrasonic welding).

7. **OPTIONAL — add per-track granularity to AFP model.** Real tapelaying data is per-Track (Run M), each with attributes `run_number`, `track_number`, `silicone_roll_change`, `towborder_direction` and 1 TS ref + 5 file refs. The seed could add a track-level loop inside AFP layup to match this structure.

8. **OPTIONAL — add "AF_N" weld-foot granularity to frame welding.** Each of the 24 C-frames has ~13 weld feet (AF_1…AF_13), each with its own process-data document. This matches the 14-module bridge and the real data hierarchy.

---

## Summary

The synthetic seed has a correct high-level intuition (AFP → NDT → stringer welding → frame welding → LBR cleat installation) and several correctly-valued parameters (6 bar nip pressure, ~125 mm/s layup speed, LBR iiwa channel architecture, bridge welding terminology). The primary inaccuracies are: (a) the Q1/Q2 panel topology applies to the lower shell, not DLR's upper shell; (b) stringer welding is ultrasonic, not resistance — the seed gives it resistance-welding channels; (c) the bridge welding channel set is a simplified single-head approximation of the real dual-head 14-channel instrument; (d) `tcp_temp_C` is a robot-pose concept, not a thermal sensor label. Changes 1–4 above would make the seed materially more accurate without restructuring it.
