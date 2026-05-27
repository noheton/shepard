"""Deterministic synthetic-data generator for the MFFD AFP manufacturing showcase.

Produces CSV timeseries, JSON quality records, and Markdown process-document stubs
that ``seed.py`` ingests into a running Shepard backend.  All values are fabricated
with a fixed RNG seed — **NOT real DLR MFFD data**.

Inspired by the process chain of the MFFD upper fuselage demonstrator manufactured
at ZLP Augsburg (DLR) using CF/LMPAEK thermoplastic CFRP without autoclave.
The MFFD won the JEC World Innovation Award 2025 (Aerospace — Parts).

Run::

    python data/generate.py [--out data]

Output layout::

    data/timeseries/   afp-s1-*.csv (6 process + 6 TCP pose), stringer-s1-*.csv, frame-punkt-*.csv, …
                       lbr-*.csv (7 joints + 6 F/T + 6 TCP pose), …
    data/structured/   ndt-s1-report.json, stringer-s1-quality.json, …
    data/files/        afp-layup-recipe-s1.md, rework-s1-protocol.md, …
    data/manifest.json
"""

from __future__ import annotations

import argparse
import csv
import json
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path

import numpy as np

# ── determinism ────────────────────────────────────────────────────────────────
RNG_SEED = 2024

# ── campaign timeline (UTC) ────────────────────────────────────────────────────
CAMPAIGN_START = datetime(2024, 7, 15, 7, 0, 0, tzinfo=timezone.utc)

# ── sample rates and durations ─────────────────────────────────────────────────
# AFP slow process: 1 Hz, 10-min representative slice (real layup is ~8 h)
AFP_HZ, AFP_S = 1, 600
# Continuous ultrasonic welding: 10 Hz, 2 min per section
WELD_HZ, WELD_S = 10, 120
# Frame spot / bridge welding: 10 Hz, 90 s
FRAME_HZ, FRAME_S = 10, 90
# Assembly alignment sequence: 1 Hz, 5 min
ASSY_HZ, ASSY_S = 1, 300
# LBR iiwa cleat installation: 10 Hz, 2 min
LBR_HZ, LBR_S = 10, 120

NS = 1_000_000_000  # nanoseconds per second


@dataclass(frozen=True)
class Ch:
    """Channel specification: nominal value, Gaussian noise std-dev, unit label."""
    nom: float
    std: float
    unit: str
    ramp: float = 0.0  # warm-up seconds before nominal is reached


# ── AFP channels ──────────────────────────────────────────────────────────────
AFP_CH: dict[str, Ch] = {
    "nip_point_temp_C":      Ch(155.0,  3.0, "°C",   ramp=60.0),   # nip-point temperature (IR camera at consolidation roller)
    "consolidation_force_N": Ch(280.0, 15.0, "N",    ramp=30.0),   # AFP nip-roll force
    "layup_speed_mm_s":      Ch(120.0,  8.0, "mm/s", ramp=20.0),   # head traverse speed
    "head_temp_C":           Ch(215.0,  5.0, "°C",   ramp=120.0),  # heating head
    "substrate_temp_C":      Ch(160.0,  4.0, "°C",   ramp=180.0),  # panel surface (LM-PAEK crystallinity requires 160–200 °C tooling temperature)
    "nip_pressure_bar":      Ch(  6.5,  0.3, "bar"),                # consolidation pressure
}

# ── AFP TCP robot-pose channels (KUKA KR270 R2700, ceiling-mount) ─────────────
# 6D Cartesian pose of the AFP head tool-centre-point in the mold frame.
# The mold section is ~2000 mm wide (X) × ~8000 mm long (Y).  Each ply is
# 16 mm tape so one pass covers 16 mm in Y.  A representative 10-min slice
# covers ~18 plies (S1 early plies).  Nominal clearance above mold: 80 mm.
AFP_TCP_NAMES = ["tcp_x_mm", "tcp_y_mm", "tcp_z_mm", "tcp_rx_deg", "tcp_ry_deg", "tcp_rz_deg"]
# LBR TCP pose channels (KUKA LBR iiwa 14 R820)
LBR_TCP_NAMES = ["tcp_x_mm", "tcp_y_mm", "tcp_z_mm", "tcp_rx_deg", "tcp_ry_deg", "tcp_rz_deg"]

# ── Continuous ultrasonic welding channels (stringers) ─────────────────────────
WELD_CH: dict[str, Ch] = {
    "sonotrode_amplitude_um": Ch( 45.0,  3.0, "µm"),         # ultrasonic amplitude at horn tip
    "weld_force_N":           Ch(600.0, 25.0, "N"),           # pneumatic downforce
    "traverse_speed_mm_s":    Ch( 10.0,  0.8, "mm/s"),        # tool traverse along stringer
    "contact_temp_C":         Ch(340.0, 15.0, "°C",ramp=5.0), # near-field IR at weld zone
    "weld_energy_J":          Ch(  8.5,  0.6, "J"),           # cumulative energy per mm
}

# ── Assembly alignment channels ───────────────────────────────────────────────
ASSY_CH: dict[str, Ch] = {
    "alignment_dx_mm": Ch(0.0,   0.02, "mm"),
    "alignment_dy_mm": Ch(0.0,   0.02, "mm"),
    "clamp_force_kN":  Ch(45.0,  2.0,  "kN"),
    "joint_temp_C":    Ch(150.0, 8.0,  "°C", ramp=60.0),
}

# ── LBR iiwa channels ─────────────────────────────────────────────────────────
LBR_JOINTS = [f"j{i}_deg" for i in range(1, 8)]
LBR_FT     = ["force_x_N", "force_y_N", "force_z_N",
               "torque_x_Nm", "torque_y_Nm", "torque_z_Nm"]
LBR_JOINT_NOM = [15.0, -30.0,  0.0, 60.0,  0.0, -45.0, 90.0]
LBR_JOINT_STD = [ 2.0,   2.0,  1.5,  2.0,  1.5,   2.0,  1.5]
LBR_FT_NOM    = [ 0.0,   0.0, -15.0, 0.0,  0.0,   0.5]
LBR_FT_STD    = [ 1.5,   1.5,   3.0, 0.2,  0.2,   0.2]


# ── helpers ───────────────────────────────────────────────────────────────────
def _ts(base: datetime, step_s: float, i: int, hz: int) -> int:
    return int((base + timedelta(seconds=step_s + i / hz)).timestamp() * NS)


def _csv(path: Path, rows: list[tuple[int, float]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["timestamp_ns", "value"])
        w.writerows(rows)


# ── generators ────────────────────────────────────────────────────────────────
def gen_afp(rng: np.random.Generator, out: Path, panel: str, anomaly: bool) -> None:
    """AFP layup telemetry.  panel is "s1" or "s2" (sections of the single 8m upper-shell skin).

    S1 anomaly: ``consolidation_force_N`` drops ~32 % and ``nip_point_temp_C`` spikes
    +18 °C at t = 280–320 s (ply-5 interval), simulating the transient resin-starved
    zone that active thermography later detects as a 45 cm² delamination at rib
    station 7.

    TCP robot-pose channels (KUKA KR270 R2700, ceiling-mount):
    - tcp_x_mm  : triangular sweep 0 → 2000 → 0 → … (one traverse ≈ 16.7 s @ 120 mm/s)
    - tcp_y_mm  : monotonically advances +16 mm/pass (tape width) over 18 plies
    - tcp_z_mm  : ≈ 80 mm clearance above mold surface (small noise)
    - tcp_rx_deg: small roll noise ±2° (surface-following)
    - tcp_ry_deg: follows barrel curvature; ≈ 8° × sin(2π x / 2000) (CFRP mold cross-section)
    - tcp_rz_deg: 0° on forward pass, 180° on return (head flip at each turnaround)
    """
    t0 = 0.0 if panel == "s1" else 3600.0  # S2 starts 1 h after S1 (parallel)
    n = AFP_S * AFP_HZ
    for name, ch in AFP_CH.items():
        rows: list[tuple[int, float]] = []
        for i in range(n):
            t_s = i / AFP_HZ
            ts = _ts(CAMPAIGN_START, t0, i, AFP_HZ)
            ramp = min(1.0, t_s / ch.ramp) if ch.ramp else 1.0
            val = ch.nom * ramp + rng.normal(0, ch.std)
            if anomaly and 280 <= t_s <= 320:
                if name == "consolidation_force_N":
                    val = ch.nom * 0.68 + rng.normal(0, ch.std * 2)
                elif name == "nip_point_temp_C":
                    val = ch.nom + 18.0 + rng.normal(0, ch.std * 1.5)
            rows.append((ts, round(val, 4)))
        _csv(out / "timeseries" / f"afp-{panel}-{name}.csv", rows)

    # ── TCP robot-pose (6D) ───────────────────────────────────────────────────
    # Traverse period: 2000 mm / 120 mm/s = 16.667 s one-way.
    traverse_s = 2000.0 / 120.0
    t_arr = np.arange(n) / AFP_HZ
    # Normalised position within a single traverse (0→1→0 sawtooth via abs(triangle))
    phase_norm = (t_arr / traverse_s) % 2.0          # 0→1 forward, 1→2 return
    forward = phase_norm < 1.0
    x_pos = np.where(forward, phase_norm, 2.0 - phase_norm) * 2000.0  # mm
    # Y advances by tape width per half-traverse
    pass_num = np.floor(t_arr / traverse_s)
    y_pos = pass_num * 16.0                           # 16 mm tape width per pass
    z_pos = np.full(n, 80.0)                          # 80 mm clearance
    rx = rng.normal(0, 2.0, n)                        # roll ±2° noise
    ry = 8.0 * np.sin(2 * np.pi * x_pos / 2000.0) + rng.normal(0, 1.0, n)  # curvature following
    rz = np.where(forward, 0.0, 180.0) + rng.normal(0, 0.5, n)  # head flip

    for ch_name, vals in zip(AFP_TCP_NAMES, [x_pos, y_pos, z_pos, rx, ry, rz]):
        rows = [(_ts(CAMPAIGN_START, t0, i, AFP_HZ), round(float(vals[i]), 4)) for i in range(n)]
        _csv(out / "timeseries" / f"afp-{panel}-{ch_name}.csv", rows)

    total_ch = len(AFP_CH) + len(AFP_TCP_NAMES)
    print(f"  AFP {panel.upper()}: {total_ch} channels × {AFP_S} s @ {AFP_HZ} Hz"
          + (" [anomaly seeded]" if anomaly else ""), flush=True)


def gen_stringer(rng: np.random.Generator, out: Path, panel: str) -> None:
    """Continuous ultrasonic welding (cUS-W): 8 stringers per section, 12 s weld / 3 s reposition."""
    day = 4 * 86400
    shift = 0 if panel == "s1" else 21600  # S1 morning, S2 afternoon
    n = WELD_S * WELD_HZ
    t = np.arange(n) / WELD_HZ
    active = (t % 15.0) < 12.0           # True during weld pass
    for name, ch in WELD_CH.items():
        rows: list[tuple[int, float]] = []
        for i in range(n):
            ts = _ts(CAMPAIGN_START, day + shift, i, WELD_HZ)
            if active[i]:
                ramp = min(1.0, (t[i] % 15.0) / ch.ramp) if ch.ramp else 1.0
                val = ch.nom * ramp + rng.normal(0, ch.std)
            elif name in ("sonotrode_amplitude_um", "weld_force_N", "weld_energy_J"):
                val = 0.0
            elif name == "contact_temp_C":
                val = 80.0 + rng.normal(0, 5)   # cooling between passes
            else:
                val = 0.0
            rows.append((ts, round(val, 4)))
        _csv(out / "timeseries" / f"stringer-{panel}-{name}.csv", rows)
    print(f"  Stringer welding {panel.upper()}: {len(WELD_CH)} channels × {WELD_S} s @ {WELD_HZ} Hz", flush=True)


def gen_frame(rng: np.random.Generator, out: Path, step: str) -> None:
    """Schweißbrücke (bridge welding): 14-channel dual-head resistance welding for C-frame attachment.

    Both Punktschweißen and Brückenschweißen use the same 14-channel spec;
    BridgePosition ramps linearly from 856 to 7841 mm across the weld sequence
    (simulating the weld head traversing the bridge).  CM_p, W1_U, W1_p, W2_p
    are correlated as observed in the real data.
    """
    day = 8 * 86400
    shift = 0 if step == "punkt" else 18000
    n = FRAME_S * FRAME_HZ

    bridge_pos = np.linspace(856.0, 7841.0, n)
    # Correlated channels — CM_p drives W1_U, W1_p, W2_p
    cm_p_base = rng.normal(4.0, 0.3, n).clip(3.0, 5.0)
    w1_u_base = 22.0 + (cm_p_base - 4.0) * 5.0 + rng.normal(0, 0.5, n)
    w1_p_base = 0.40 + (cm_p_base - 4.0) * 0.04 + rng.normal(0, 0.02, n)
    w2_p_base = cm_p_base + rng.normal(0, 0.05, n)
    w2_u_base = w1_u_base * 0.62 + rng.normal(0, 0.3, n)

    channels: dict[str, np.ndarray] = {
        "BridgePosition": bridge_pos,
        "CM_I":   np.full(n, 5.0) + rng.normal(0, 0.05, n),
        "CM_p":   cm_p_base,
        "CM_t":   np.full(n, 5.0) + rng.normal(0, 0.02, n),
        "W1_I":   np.full(n, 17.5) + rng.normal(0, 0.3, n),
        "W1_U":   w1_u_base.clip(18.0, 28.0),
        "W1_p":   w1_p_base.clip(0.25, 0.55),
        "W1_t":   np.full(n, 65.0) + rng.normal(0, 0.1, n),
        "W2_I":   np.full(n, 8.0) + rng.normal(0, 0.05, n),
        "W2_U":   w2_u_base.clip(11.0, 16.0),
        "W2_p":   w2_p_base.clip(3.0, 5.0),
        "W2_t":   np.full(n, 120.0) + rng.normal(0, 0.1, n),
        "WC_I":   np.full(n, 5.0) + rng.normal(0, 0.05, n),
        "WC_t":   np.full(n, 120.0) + rng.normal(0, 0.1, n),
    }
    for name, vals in channels.items():
        rows = [(_ts(CAMPAIGN_START, day + shift, i, FRAME_HZ), round(float(vals[i]), 4)) for i in range(n)]
        _csv(out / "timeseries" / f"frame-{step}-{name}.csv", rows)
    print(f"  Frame welding ({step}): 14 channels × {FRAME_S} s @ {FRAME_HZ} Hz", flush=True)


def gen_assembly(rng: np.random.Generator, out: Path) -> None:
    """S1 + S2 Stringerverbindung: dx/dy converge to zero; clamp ramps; joint heats."""
    day = 10 * 86400
    n = ASSY_S * ASSY_HZ
    t = np.arange(n) / ASSY_HZ
    for name, ch in ASSY_CH.items():
        rows: list[tuple[int, float]] = []
        for i in range(n):
            ts = _ts(CAMPAIGN_START, float(day), i, ASSY_HZ)
            if name in ("alignment_dx_mm", "alignment_dy_mm"):
                # misalignment converges from ~0.8 mm to zero over 120 s
                val = ch.nom + max(0.0, 1.0 - t[i] / 120.0) * 0.8 + rng.normal(0, ch.std)
            elif name == "clamp_force_kN":
                val = ch.nom * min(1.0, t[i] / 60.0) + rng.normal(0, ch.std)
            else:
                ramp = min(1.0, t[i] / ch.ramp) if ch.ramp else 1.0
                val = ch.nom * ramp + rng.normal(0, ch.std)
            rows.append((ts, round(val, 4)))
        _csv(out / "timeseries" / f"assembly-{name}.csv", rows)
    print(f"  Assembly alignment: {len(ASSY_CH)} channels × {ASSY_S} s @ {ASSY_HZ} Hz", flush=True)


def gen_lbr(rng: np.random.Generator, out: Path) -> None:
    """LBR iiwa: 7 joints + 6-axis F/T + 6D TCP pose.
    42 cleats, ~2.7 s per cleat (reach → insert → withdraw).

    TCP Cartesian pose (KUKA LBR iiwa 14 R820, base fixed on assembly rig):
    - tcp_x/y_mm: robot reaches ~350 mm out in XY; x oscillates ±30 mm per cleat
    - tcp_z_mm  : descends from ~300 mm (rest) to ~230 mm (contact) during insert phase
    - tcp_rx/ry_deg: tool tip orientation ±5° noise (force-torque guidance)
    - tcp_rz_deg: rotates per cleat position (42 evenly spaced around 360° arc)
    """
    day = 11 * 86400
    n = LBR_S * LBR_HZ
    t = np.arange(n) / LBR_HZ
    phase = t % 2.7          # position within a single cleat installation cycle

    for j, name in enumerate(LBR_JOINTS):
        rows: list[tuple[int, float]] = []
        for i in range(n):
            ts = _ts(CAMPAIGN_START, float(day), i, LBR_HZ)
            p = float(phase[i])
            motion = 5.0 * np.sin(2 * np.pi * p / 2.0) if p < 2.0 else 0.0
            val = LBR_JOINT_NOM[j] + motion + rng.normal(0, LBR_JOINT_STD[j])
            rows.append((ts, round(val, 4)))
        _csv(out / "timeseries" / f"lbr-{name}.csv", rows)

    for ft, name in enumerate(LBR_FT):
        rows = []
        for i in range(n):
            ts = _ts(CAMPAIGN_START, float(day), i, LBR_HZ)
            p = float(phase[i])
            val = LBR_FT_NOM[ft] + rng.normal(0, LBR_FT_STD[ft])
            if 2.0 <= p < 2.7 and name == "force_z_N":
                val -= 25.0   # contact-force spike during cleat insertion
            rows.append((ts, round(val, 4)))
        _csv(out / "timeseries" / f"lbr-{name}.csv", rows)

    # ── LBR TCP Cartesian pose ────────────────────────────────────────────────
    # Cleat index = floor(t / 2.7); 42 cleats evenly spaced on a circular arc
    cleat_idx = np.floor(t / 2.7).astype(int)
    cleat_angle_deg = (cleat_idx % 42) * (360.0 / 42.0)  # 0…360° arc
    # XY reach: nominal 350 mm out + small radial variation
    reach = 350.0 + rng.normal(0, 5.0, n)
    tcp_x = reach * np.cos(np.radians(cleat_angle_deg)) + rng.normal(0, 2.0, n)
    tcp_y = reach * np.sin(np.radians(cleat_angle_deg)) + rng.normal(0, 2.0, n)
    # Z: descends to ~230 mm during insertion phase (phase 2.0–2.7 s)
    insert = (phase >= 2.0) & (phase < 2.7)
    tcp_z = np.where(insert, 230.0, 295.0) + rng.normal(0, 3.0, n)
    tcp_rx = rng.normal(0, 4.0, n)   # tool tip orientation noise
    tcp_ry = rng.normal(0, 4.0, n)
    tcp_rz = cleat_angle_deg + rng.normal(0, 1.0, n)  # tool points toward cleat

    for ch_name, vals in zip(LBR_TCP_NAMES, [tcp_x, tcp_y, tcp_z, tcp_rx, tcp_ry, tcp_rz]):
        rows = [(_ts(CAMPAIGN_START, float(day), i, LBR_HZ), round(float(vals[i]), 4)) for i in range(n)]
        _csv(out / "timeseries" / f"lbr-{ch_name}.csv", rows)

    total_ch = len(LBR_JOINTS) + len(LBR_FT) + len(LBR_TCP_NAMES)
    print(f"  LBR robot: {total_ch} channels × {LBR_S} s @ {LBR_HZ} Hz", flush=True)


def gen_structured(out: Path) -> None:
    """NDT thermography reports and stringer quality logs as JSON."""
    sd = out / "structured"
    sd.mkdir(parents=True, exist_ok=True)

    # S1 FAIL — delamination found
    (sd / "ndt-s1-report.json").write_text(json.dumps({
        "panel": "S1",
        "inspection_date": "2024-07-16T09:00:00Z",
        "inspector": "C. Wagner",
        "method": "Active Thermography",
        "equipment": "FLIR X6900sc (IR-CAM-02)",
        "result": "FAIL",
        "defects": [{
            "id": "DEF-001",
            "type": "delamination",
            "rib_station": 7,
            "area_cm2": 45.2,
            "depth_mm": 0.4,
            "acceptance_limit_cm2": 20.0,
            "disposition": "rework required",
        }],
        "notes": (
            "Delamination DEF-001 at rib station 7 exceeds acceptance limit "
            "(45.2 cm² > 20 cm²).  AFP process log shows consolidation force drop "
            "at t = 280–320 s (ply 5).  Root cause: transient head-temperature "
            "excursion during reel change."
        ),
    }, indent=2))

    # S2 PASS
    (sd / "ndt-s2-report.json").write_text(json.dumps({
        "panel": "S2",
        "inspection_date": "2024-07-16T13:30:00Z",
        "inspector": "C. Wagner",
        "method": "Active Thermography",
        "equipment": "FLIR X6900sc (IR-CAM-02)",
        "result": "PASS",
        "defects": [],
        "notes": "No defects above 20 cm² threshold detected.  S2 cleared for stringer welding.",
    }, indent=2))

    # S1 re-check PASS (after rework)
    (sd / "ndt-s1-recheck.json").write_text(json.dumps({
        "panel": "S1",
        "inspection_date": "2024-07-18T11:00:00Z",
        "inspector": "C. Wagner",
        "method": "Active Thermography",
        "equipment": "FLIR X6900sc (IR-CAM-02)",
        "result": "PASS",
        "rework_reference": "REWORK-S1-001",
        "defects": [],
        "notes": (
            "Re-inspection after rework REWORK-S1-001.  "
            "No defects above threshold at rib station 7 or adjacent areas.  "
            "S1 cleared for stringer welding."
        ),
    }, indent=2))

    # Stringer quality logs (S1 and S2)
    rng_q = np.random.default_rng(42)
    for panel in ("s1", "s2"):
        stringers = [
            {
                "stringer_id": f"STR-S{panel[-1].upper()}-{i:02d}",
                "weld_result": "PASS",
                "peak_contact_temp_C": round(float(340.0 + rng_q.uniform(-10, 20)), 1),
                "avg_force_N": round(float(600.0 + rng_q.uniform(-15, 15)), 1),
                "pull_test_N": round(float(2850.0 + rng_q.uniform(-100, 100)), 1),
                "spec_min_pull_test_N": 2500,
            }
            for i in range(1, 9)
        ]
        (sd / f"stringer-{panel}-quality.json").write_text(json.dumps({
            "panel": panel.upper(),
            "weld_date": "2024-07-19" if panel == "s1" else "2024-07-21",
            "operator": "R. Hoffmann" if panel == "s1" else "K. Neumann",
            "process": "cUS-W",
            "result": "PASS",
            "stringers": stringers,
        }, indent=2))

    print(f"  Structured: 5 JSON reports in {sd}", flush=True)


def gen_files(out: Path) -> None:
    """Markdown process-document stubs (process recipes, protocols, specs)."""
    fd = out / "files"
    fd.mkdir(parents=True, exist_ok=True)

    (fd / "afp-layup-recipe-s1.md").write_text(
        "# AFP Layup Recipe — S1 Section (upper shell) (synthetic)\n\n"
        "**Material:** CF/LMPAEK 16 mm tape (lot CF-LMPAEK-2024-07-A)  \n"
        "**Equipment:** AFPT MTLH on KUKA KR270 R2700 ceiling-mount robot  \n"
        "**Total plies:** 18 | **Stacking:** [0/90/±45/0/90/±45/0/90]s\n\n"
        "| Ply | Angle | Speed (mm/s) | Nip-pt (°C) | Force (N) | Note |\n"
        "|-----|-------|-------------|-------------|-----------|------|\n"
        "| 1   | 0°   | 120         | 155         | 280       | nominal |\n"
        "| 2   | 90°  | 120         | 155         | 280       | nominal |\n"
        "| 3   | +45° | 100         | 158         | 290       | nominal |\n"
        "| 4   | -45° | 100         | 158         | 290       | nominal |\n"
        "| 5   | 0°   | 120         | 155         | 280       |"
        " **⚠ anomaly t=280–320 s: force drop + nip-pt temp spike** |\n"
        "| 6–18| …    | …           | …           | …         | nominal |\n\n"
        "_Synthetic showcase data.  Not real DLR MFFD data._\n"
    )
    (fd / "afp-layup-recipe-s2.md").write_text(
        "# AFP Layup Recipe — S2 Section (upper shell) (synthetic)\n\n"
        "**Material:** CF/LMPAEK 16 mm tape (lot CF-LMPAEK-2024-07-B)  \n"
        "**Equipment:** AFPT MTLH on KUKA KR270 R2700 ceiling-mount robot  \n"
        "**Total plies:** 18 | **Stacking:** [0/90/±45/0/90/±45/0/90]s\n\n"
        "All 18 plies within specification.  S2 cleared for NDT on 2024-07-16.\n\n"
        "_Synthetic showcase data.  Not real DLR MFFD data._\n"
    )
    (fd / "rework-s1-protocol.md").write_text(
        "# Rework Protocol REWORK-S1-001 (synthetic)\n\n"
        "**Date:** 2024-07-17 | **Operator:** A. Fischer  \n"
        "**Reference defect:** DEF-001 — rib station 7, 45.2 cm², depth 0.4 mm\n\n"
        "## Procedure\n"
        "1. Abrasion of delaminated area (120-grit)\n"
        "2. Surface cleaning — IPA wipe\n"
        "3. Hand layup of 2 additional CF/LMPAEK plies (0°/90°)\n"
        "4. Consolidation press: 6.0 bar, 160 °C, 45 min\n"
        "5. Cool-down to < 50 °C before release\n\n"
        "## Outcome\n"
        "Rework area 45 cm².  Released for re-inspection "
        "(see `ndt-s1-recheck.json`).\n\n"
        "_Synthetic showcase data.  Not real DLR MFFD data._\n"
    )
    (fd / "welding-protocol.md").write_text(
        "# Continuous Ultrasonic Welding (cUS-W) Protocol Template (synthetic)\n\n"
        "**Process:** Continuous Ultrasonic Welding (cUS-W) — robot-based  \n"
        "**Material system:** CF/LMPAEK  \n"
        "**Sonotrode amplitude:** 45 µm ± 3 µm  \n"
        "**Weld force:** 600 N ± 25 N  \n"
        "**Traverse speed:** 10 mm/s ± 0.8 mm/s  \n"
        "**Target weld-zone temp:** 340 °C ± 15 °C\n\n"
        "## Acceptance criteria\n"
        "- Peak contact temp: 310–370 °C\n"
        "- Average force: 575–625 N\n"
        "- Pull test per stringer: ≥ 2 500 N\n\n"
        "_Synthetic showcase data. Not real DLR MFFD data._\n"
    )
    (fd / "lbr-cleat-spec.md").write_text(
        "# LBR iiwa Cleat Installation Specification (synthetic)\n\n"
        "**Robot:** KUKA LBR iiwa 14 R820  \n"
        "**Cleats:** Ti-6Al-4V, 42 pieces  \n"
        "**Nominal insertion force (force_z_N):** −25 N ± 5 N  \n"
        "**Nominal torque (torque_z_Nm):** 0.5 ± 0.2 Nm\n\n"
        "| Joint | Nominal (°) | Range |\n"
        "|-------|------------|-------|\n"
        "| J1    | +15        | ±170° |\n"
        "| J2    | −30        | ±120° |\n"
        "| J3    |   0        | ±170° |\n"
        "| J4    | +60        | ±120° |\n"
        "| J5    |   0        | ±170° |\n"
        "| J6    | −45        | ±120° |\n"
        "| J7    | +90        | ±175° |\n\n"
        "_Synthetic showcase data.  Not real DLR MFFD data._\n"
    )
    print(f"  Files: 5 Markdown stubs in {fd}", flush=True)


def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser(description="Generate MFFD showcase synthetic data")
    ap.add_argument("--out", default="data", help="output directory (default: data/)")
    args = ap.parse_args(argv)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    rng = np.random.default_rng(RNG_SEED)
    print("Generating MFFD showcase synthetic data …", flush=True)
    gen_afp(rng, out, "s1", anomaly=True)
    gen_afp(rng, out, "s2", anomaly=False)
    gen_stringer(rng, out, "s1")
    gen_stringer(rng, out, "s2")
    gen_frame(rng, out, "punkt")
    gen_frame(rng, out, "bruecke")
    gen_assembly(rng, out)
    gen_lbr(rng, out)
    gen_structured(out)
    gen_files(out)

    all_files = sorted(p.relative_to(out) for p in out.rglob("*") if p.is_file())
    (out / "manifest.json").write_text(json.dumps({
        "generator": "mffd-showcase/data/generate.py",
        "generated_at": datetime.now(tz=timezone.utc).isoformat(),
        "rng_seed": RNG_SEED,
        "note": "Synthetic data.  NOT real DLR MFFD data.",
        "files": [str(f) for f in all_files],
    }, indent=2))
    print(f"\nDone.  {len(all_files)} files written to {out}/", flush=True)


if __name__ == "__main__":
    main()
