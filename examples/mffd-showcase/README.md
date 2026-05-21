# MFFD AFP Manufacturing Campaign — Shepard Showcase

Synthetic dataset inspired by the **MFFD upper fuselage demonstrator** manufactured
at ZLP Augsburg (DLR) using CF/LMPAEK thermoplastic CFRP — without autoclave.
The MFFD won the **JEC World Innovation Award 2025 (Aerospace — Parts)**.

**NOT real DLR MFFD data.** All values are deterministic synthetic outputs of
`data/generate.py` (numpy.random.default_rng(2024)).

---

## The story

Two quarter shells (Q1, Q2) are laid up by an AFP robot, inspected by active
thermography, resistance-welded with stringers and frames, assembled into a full
panel, and fitted with titanium cleats by a KUKA LBR iiwa collaborative robot.

### The anomaly (the reason to use Shepard)

During Q1 layup, ply 5 shows a **consolidation force drop + TCP temperature spike**
at t = 280–320 s (reel change). The downstream thermography inspection confirms a
**45.2 cm² delamination at rib station 7** — above the 20 cm² acceptance limit.

A rework loop is recorded as a Predecessor/Successor chain:

```
AFP Layup Q1  →  NDT Q1 [FAIL]  →  Rework Q1  →  NDT Q1 Re-check [PASS]  →  Stringer Q1
```

**Why this matters:** a folder hierarchy (`Q1/layup/`, `Q1/ndt/`, `Q1/rework/`) puts
the rework in a sibling folder with no machine-readable causal link.
Shepard's provenance graph makes the rework loop self-evident to an auditor,
a QMS system, and an AI agent — without any additional metadata conventions.

---

## Process chain (DataObject DAG)

```
AFP Layup Q1 ──► NDT Q1 [FAIL] ──► Rework Q1 ──► NDT Q1 Recheck [PASS] ──► Stringer Q1 ──┐
AFP Layup Q2 ──────────────────────────────────► NDT Q2 [PASS]           ──► Stringer Q2 ──┤
                                                                                             ├──► Frame Welding (Punkt) ──► Frame Welding (Brücke) ──► Stringerverbindung ──► LBR Cleats
                                                                                             └───────────────────────────────────────────────────────────────────────────────────────────┘
```

The **two parallel tracks** (Q1 and Q2) merging at Frame Welding is a DAG pattern that
no single-parent folder structure can express.

---

## DataObjects and data types

| Step | DataObject | TS channels | Structured | Files |
|------|-----------|-------------|-----------|-------|
| AFP Layup Q1 | `AFP Layup — Q1 Shell` | 6 @ 1 Hz (with anomaly) | — | Layup recipe |
| AFP Layup Q2 | `AFP Layup — Q2 Shell` | 6 @ 1 Hz (nominal) | — | Layup recipe |
| NDT Q1 | `NDT Thermography — Q1 (FAIL)` | — | ndt-q1-report.json | — |
| NDT Q2 | `NDT Thermography — Q2 (PASS)` | — | ndt-q2-report.json | — |
| Rework Q1 | `Rework — Q1 Rib Station 7` | — | — | Rework protocol |
| NDT Q1 recheck | `NDT Thermography — Q1 Re-check (PASS)` | — | ndt-q1-recheck.json | — |
| Stringer Q1 | `Stringer Welding — Q1` | 5 @ 10 Hz (pulsed) | stringer-q1-quality.json | Welding protocol |
| Stringer Q2 | `Stringer Welding — Q2` | 5 @ 10 Hz (pulsed) | stringer-q2-quality.json | Welding protocol |
| Frame Punkt | `Frame Welding — Punktschweißen` | 5 @ 10 Hz (spot bursts) | — | — |
| Frame Brücke | `Frame Welding — Brückenschweißen` | 5 @ 10 Hz (spot bursts) | — | — |
| Assembly | `Stringerverbindung — Q1 + Q2 Assembly` | 4 @ 1 Hz (alignment) | — | — |
| LBR Cleats | `Cleat Installation — LBR iiwa` | 13 @ 10 Hz (joints + F/T) | — | Cleat spec |

---

## Synthetic channel highlights

### AFP Robot (6 channels @ 1 Hz, 10 min)
- `tcp_temp_C` — tool-centre-point temperature (nominal 155 °C; spike +18 °C at ply 5)
- `consolidation_force_N` — AFP nip-roll force (nominal 280 N; drop to ~190 N at ply 5)
- `layup_speed_mm_s`, `head_temp_C`, `substrate_temp_C`, `nip_pressure_bar`

### Stringer Welding (5 channels @ 10 Hz, 2 min, 8 stringers)
- `weld_current_A` (650 A nominal), `weld_voltage_V`, `weld_force_N`, `weld_zone_temp_C` (330 °C),
  `traverse_speed_mm_s` — pulsed: 12 s active / 3 s reposition

### Frame Welding (5 channels @ 10 Hz, 90 s, spot bursts)
- `spot_current_A` (4 500 A peak), `spot_voltage_V`, `spot_force_N`, `electrode_temp_C`,
  `displacement_mm` — pulsed: 0.5 s active / 2.5 s idle

### Assembly Alignment (4 channels @ 1 Hz, 5 min)
- `alignment_dx_mm`, `alignment_dy_mm` — converge to < 0.1 mm over first 120 s
- `clamp_force_kN`, `joint_temp_C`

### LBR iiwa (13 channels @ 10 Hz, 2 min, 42 cleats)
- `j1_deg`…`j7_deg` — joint angles (oscillate ±5° during reach; hold at insert)
- `force_x_N`, `force_y_N`, `force_z_N` — contact force spike −25 N during cleat insertion
- `torque_x_Nm`, `torque_y_Nm`, `torque_z_Nm`

---

## Usage

### 1. Generate synthetic data

```bash
cd examples/mffd-showcase
python data/generate.py
# Writes timeseries CSVs, structured JSON, and Markdown stubs to data/
```

### 2. Seed into a running Shepard backend

```bash
python seed.py \
  --host https://shepard.example.com/shepard/api \
  --apikey YOUR_API_KEY

# Or with env vars:
BACKEND_URL=http://localhost:8080/shepard/api API_KEY=xxx python seed.py

# Re-generate data and seed in one step:
python seed.py --host … --apikey … --regenerate

# Full re-seed (delete existing collection first):
python seed.py --host … --apikey … --reset
```

### 3. Explore in Shepard

1. Open the **MFFD Upper Fuselage Demonstrator** collection
2. Open **AFP Layup — Q1 Shell** → view the timeseries chart → spot the force drop
   and temperature spike at t = 280–320 s
3. Follow the Predecessor link to **NDT Thermography — Q1 (FAIL)** → read the
   structured report and lab journal entry
4. Follow to **Rework — Q1 Rib Station 7** → then **NDT Q1 Re-check (PASS)**
5. The **lineage graph** on the collection page shows the full DAG with the
   Q1 rework loop clearly distinct from the clean Q2 track

---

## Dependencies

Same as `examples/lumen-showcase/`:

```
pip install shepard-client numpy
```

---

## Notes

- This is a **synthetic showcase** — not a representation of any specific MFFD
  test campaign. Real MFFD process data will be ingested separately when available.
- The anomaly seed values (force 280 → 190 N, temperature +18 °C) are plausible
  for a CF/LMPAEK AFP process but are synthetic.
- The MFFD demo data (real robot logs) from ZLP Augsburg will overlay this
  structure when the data package is received.
