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

    data/timeseries/   afp-q1-*.csv, stringer-q1-*.csv, frame-punkt-*.csv, …
    data/structured/   ndt-q1-report.json, stringer-q1-quality.json, …
    data/files/        afp-layup-recipe-q1.md, rework-q1-protocol.md, …
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
# Continuous resistance welding: 10 Hz, 2 min per panel
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
    "tcp_temp_C":            Ch(155.0,  3.0, "°C",   ramp=60.0),   # tool-centre-point temperature
    "consolidation_force_N": Ch(280.0, 15.0, "N",    ramp=30.0),   # AFP nip-roll force
    "layup_speed_mm_s":      Ch(120.0,  8.0, "mm/s", ramp=20.0),   # head traverse speed
    "head_temp_C":           Ch(215.0,  5.0, "°C",   ramp=120.0),  # heating head
    "substrate_temp_C":      Ch( 85.0,  4.0, "°C",   ramp=180.0), # panel surface
    "nip_pressure_bar":      Ch(  6.5,  0.3, "bar"),                # consolidation pressure
}

# ── Continuous resistance welding channels (stringers) ────────────────────────
WELD_CH: dict[str, Ch] = {
    "weld_current_A":      Ch(650.0, 30.0, "A"),
    "weld_voltage_V":      Ch( 12.5,  0.8, "V"),
    "weld_force_N":        Ch(520.0, 25.0, "N"),
    "weld_zone_temp_C":    Ch(330.0, 12.0, "°C"),
    "traverse_speed_mm_s": Ch( 15.0,  1.5, "mm/s"),
}

# ── Frame spot / bridge welding channels ──────────────────────────────────────
FRAME_CH: dict[str, Ch] = {
    "spot_current_A":   Ch(4500.0, 200.0, "A"),
    "spot_voltage_V":   Ch(   2.2,   0.2, "V"),
    "spot_force_N":     Ch(1800.0,  80.0, "N"),
    "electrode_temp_C": Ch( 185.0,  15.0, "°C"),
    "displacement_mm":  Ch(   0.8,   0.05, "mm"),
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
    """AFP layup telemetry.

    Q1 anomaly: ``consolidation_force_N`` drops ~32 % and ``tcp_temp_C`` spikes
    +18 °C at t = 280–320 s (ply-5 interval), simulating the transient resin-starved
    zone that active thermography later detects as a 45 cm² delamination at rib
    station 7.
    """
    t0 = 0.0 if panel == "q1" else 3600.0  # Q2 starts 1 h after Q1 (parallel)
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
                elif name == "tcp_temp_C":
                    val = ch.nom + 18.0 + rng.normal(0, ch.std * 1.5)
            rows.append((ts, round(val, 4)))
        _csv(out / "timeseries" / f"afp-{panel}-{name}.csv", rows)
    print(f"  AFP {panel.upper()}: {len(AFP_CH)} channels × {AFP_S} s @ {AFP_HZ} Hz"
          + (" [anomaly seeded]" if anomaly else ""), flush=True)


def gen_stringer(rng: np.random.Generator, out: Path, panel: str) -> None:
    """Continuous resistance welding: 8 stringers, 12 s weld / 3 s reposition."""
    day = 4 * 86400
    shift = 0 if panel == "q1" else 21600  # Q1 morning, Q2 afternoon
    n = WELD_S * WELD_HZ
    t = np.arange(n) / WELD_HZ
    active = (t % 15.0) < 12.0           # True during weld pass
    for name, ch in WELD_CH.items():
        rows: list[tuple[int, float]] = []
        for i in range(n):
            ts = _ts(CAMPAIGN_START, day + shift, i, WELD_HZ)
            if active[i]:
                val = ch.nom + rng.normal(0, ch.std)
            elif name in ("weld_current_A", "weld_voltage_V", "weld_force_N"):
                val = float(rng.uniform(0, 5))
            elif name == "weld_zone_temp_C":
                val = 80.0 + rng.normal(0, 5)   # cooling between passes
            else:
                val = 0.0
            rows.append((ts, round(val, 4)))
        _csv(out / "timeseries" / f"stringer-{panel}-{name}.csv", rows)
    print(f"  Stringer welding {panel.upper()}: {len(WELD_CH)} channels × {WELD_S} s @ {WELD_HZ} Hz", flush=True)


def gen_frame(rng: np.random.Generator, out: Path, step: str) -> None:
    """Spot (Punktschweißen) or bridge (Brückenschweißen) welding: 0.5 s pulse / 2.5 s idle."""
    day = 8 * 86400
    shift = 0 if step == "punkt" else 18000
    n = FRAME_S * FRAME_HZ
    t = np.arange(n) / FRAME_HZ
    active = (t % 3.0) < 0.5
    for name, ch in FRAME_CH.items():
        rows: list[tuple[int, float]] = []
        for i in range(n):
            ts = _ts(CAMPAIGN_START, day + shift, i, FRAME_HZ)
            if active[i]:
                val = ch.nom + rng.normal(0, ch.std)
            elif name in ("spot_current_A", "spot_voltage_V", "spot_force_N"):
                val = 0.0
            elif name == "electrode_temp_C":
                val = 80.0 + rng.normal(0, 8)
            else:
                val = float(rng.normal(0, 0.01))
            rows.append((ts, round(val, 4)))
        _csv(out / "timeseries" / f"frame-{step}-{name}.csv", rows)
    print(f"  Frame welding ({step}): {len(FRAME_CH)} channels × {FRAME_S} s @ {FRAME_HZ} Hz", flush=True)


def gen_assembly(rng: np.random.Generator, out: Path) -> None:
    """Q1 + Q2 Stringerverbindung: dx/dy converge to zero; clamp ramps; joint heats."""
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
    """LBR iiwa: 7 joints + 6-axis F/T. 42 cleats, ~2.7 s per cleat (reach → insert → withdraw)."""
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
    total_ch = len(LBR_JOINTS) + len(LBR_FT)
    print(f"  LBR robot: {total_ch} channels × {LBR_S} s @ {LBR_HZ} Hz", flush=True)


def gen_structured(out: Path) -> None:
    """NDT thermography reports and stringer quality logs as JSON."""
    sd = out / "structured"
    sd.mkdir(parents=True, exist_ok=True)

    # Q1 FAIL — delamination found
    (sd / "ndt-q1-report.json").write_text(json.dumps({
        "panel": "Q1",
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

    # Q2 PASS
    (sd / "ndt-q2-report.json").write_text(json.dumps({
        "panel": "Q2",
        "inspection_date": "2024-07-16T13:30:00Z",
        "inspector": "C. Wagner",
        "method": "Active Thermography",
        "equipment": "FLIR X6900sc (IR-CAM-02)",
        "result": "PASS",
        "defects": [],
        "notes": "No defects above 20 cm² threshold detected.  Q2 cleared for stringer welding.",
    }, indent=2))

    # Q1 re-check PASS (after rework)
    (sd / "ndt-q1-recheck.json").write_text(json.dumps({
        "panel": "Q1",
        "inspection_date": "2024-07-18T11:00:00Z",
        "inspector": "C. Wagner",
        "method": "Active Thermography",
        "equipment": "FLIR X6900sc (IR-CAM-02)",
        "result": "PASS",
        "rework_reference": "REWORK-Q1-001",
        "defects": [],
        "notes": (
            "Re-inspection after rework REWORK-Q1-001.  "
            "No defects above threshold at rib station 7 or adjacent areas.  "
            "Q1 cleared for stringer welding."
        ),
    }, indent=2))

    # Stringer quality logs (Q1 and Q2)
    rng_q = np.random.default_rng(42)
    for panel in ("q1", "q2"):
        stringers = [
            {
                "stringer_id": f"STR-{panel.upper()}-{i:02d}",
                "weld_result": "PASS",
                "peak_temp_C": round(float(320.0 + rng_q.uniform(5, 20)), 1),
                "avg_force_N": round(float(515.0 + rng_q.uniform(-10, 10)), 1),
                "pull_test_N": round(float(2800.0 + rng_q.uniform(-100, 100)), 1),
                "spec_min_pull_test_N": 2500,
            }
            for i in range(1, 9)
        ]
        (sd / f"stringer-{panel}-quality.json").write_text(json.dumps({
            "panel": panel.upper(),
            "weld_date": "2024-07-19" if panel == "q1" else "2024-07-21",
            "operator": "R. Hoffmann" if panel == "q1" else "K. Neumann",
            "result": "PASS",
            "stringers": stringers,
        }, indent=2))

    print(f"  Structured: 5 JSON reports in {sd}", flush=True)


def gen_files(out: Path) -> None:
    """Markdown process-document stubs (process recipes, protocols, specs)."""
    fd = out / "files"
    fd.mkdir(parents=True, exist_ok=True)

    (fd / "afp-layup-recipe-q1.md").write_text(
        "# AFP Layup Recipe — Q1 Shell (synthetic)\n\n"
        "**Material:** CF/LMPAEK 16 mm tape (lot CF-LMPAEK-2024-07-A)  \n"
        "**Equipment:** AFP Robot 01 (Coriolis Composites A-PPT 16)  \n"
        "**Total plies:** 18 | **Stacking:** [0/90/±45/0/90/±45/0/90]s\n\n"
        "| Ply | Angle | Speed (mm/s) | TCP (°C) | Force (N) | Note |\n"
        "|-----|-------|-------------|----------|-----------|------|\n"
        "| 1   | 0°   | 120         | 155      | 280       | nominal |\n"
        "| 2   | 90°  | 120         | 155      | 280       | nominal |\n"
        "| 3   | +45° | 100         | 158      | 290       | nominal |\n"
        "| 4   | -45° | 100         | 158      | 290       | nominal |\n"
        "| 5   | 0°   | 120         | 155      | 280       |"
        " **⚠ anomaly t=280–320 s: force drop + temp spike** |\n"
        "| 6–18| …    | …           | …        | …         | nominal |\n\n"
        "_Synthetic showcase data.  Not real DLR MFFD data._\n"
    )
    (fd / "afp-layup-recipe-q2.md").write_text(
        "# AFP Layup Recipe — Q2 Shell (synthetic)\n\n"
        "**Material:** CF/LMPAEK 16 mm tape (lot CF-LMPAEK-2024-07-B)  \n"
        "**Equipment:** AFP Robot 01 (Coriolis Composites A-PPT 16)  \n"
        "**Total plies:** 18 | **Stacking:** [0/90/±45/0/90/±45/0/90]s\n\n"
        "All 18 plies within specification.  Q2 cleared for NDT on 2024-07-16.\n\n"
        "_Synthetic showcase data.  Not real DLR MFFD data._\n"
    )
    (fd / "rework-q1-protocol.md").write_text(
        "# Rework Protocol REWORK-Q1-001 (synthetic)\n\n"
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
        "(see `ndt-q1-recheck.json`).\n\n"
        "_Synthetic showcase data.  Not real DLR MFFD data._\n"
    )
    (fd / "welding-protocol.md").write_text(
        "# Resistance Welding Protocol Template (synthetic)\n\n"
        "**Process:** Continuous Resistance Welding (CRW)  \n"
        "**Material system:** CF/LMPAEK  \n"
        "**Target zone temp:** 330 °C ± 20 °C  \n"
        "**Traverse speed:** 15 mm/s ± 2 mm/s  \n"
        "**Force:** 520 N ± 30 N\n\n"
        "## Acceptance criteria\n"
        "- Peak weld zone temp: 300–360 °C\n"
        "- Average force: 490–560 N\n"
        "- Pull test per stringer: ≥ 2 500 N\n\n"
        "_Synthetic showcase data.  Not real DLR MFFD data._\n"
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
    gen_afp(rng, out, "q1", anomaly=True)
    gen_afp(rng, out, "q2", anomaly=False)
    gen_stringer(rng, out, "q1")
    gen_stringer(rng, out, "q2")
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
