"""Deterministic synthetic-data generator for the LUMEN-inspired showcase.

The dataset is fictional. Numerical values are plausible for a 25 kN-class
LOX/LCH4 expander-bleed demonstrator but are synthesized with a fixed RNG
seed so the output is reproducible bit-for-bit.

Run:
    python data/generate.py [--out data]

This produces:
    data/timeseries/tr-{N}-{channel}.csv      (25 channels x 15 runs)
    data/structured/tr-{N}-runlog.json        (operator run log per run)
    data/structured/schema.json               (run-log JSON schema sketch)
    data/files/tr-{N}-cad-stub.bin            (4 KB placeholder per run)
    data/files/tr-{N}-test-report.md          (~1 KB per run)
    data/files/anomaly-photo-stub.bin         (4 KB placeholder)
    data/manifest.json                        (record of what was emitted)
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import struct
import zlib
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Iterable

import numpy as np

# ---- determinism -----------------------------------------------------------
RNG_SEED = 2024
SAMPLE_RATE_HZ = 100
DURATION_S = 30
N_SAMPLES = SAMPLE_RATE_HZ * DURATION_S  # 3000 per channel per run
N_RUNS = 15

# ---- campaign metadata -----------------------------------------------------
CAMPAIGN_START_DATE = datetime(2024, 7, 8, 9, 0, 0, tzinfo=timezone.utc)
BENCH = "P3-Lampoldshausen"
PROPELLANT = "LOX/LCH4"
TARGET_THRUST_KN = 25
TARGET_MIXTURE_RATIO = 3.4
TEST_ENGINEERS = [
    "T. Marek",       # TR-001
    "S. Holzwarth",   # TR-002
    "A. Reuter",      # TR-003
    "T. Marek",       # TR-004
    "L. Voss",        # TR-005 (hold day)
    "T. Marek",       # TR-006
    "A. Reuter",      # TR-007
    "S. Holzwarth",   # TR-008
    "T. Marek",       # TR-009
    "L. Voss",        # TR-010
    "A. Reuter",      # TR-011
    "T. Marek",       # TR-012 (hold day)
    "S. Holzwarth",   # TR-013
    "L. Voss",        # TR-014
    "T. Marek",       # TR-015
]

# ---- phases (start_s, end_s) inclusive-exclusive --------------------------
# 30 s burn divided into seven shepard-friendly phases.
PHASES: list[tuple[str, float, float]] = [
    ("precool",      0.0,  2.0),
    ("ignition",     2.0,  3.0),
    ("ramp_up",      3.0,  9.0),
    ("steady_state", 9.0, 22.0),
    ("throttle",    22.0, 26.0),
    ("shutdown",    26.0, 28.0),
    ("purge",       28.0, 30.0),
]

# ---- nominal envelopes per channel (steady-state target, units) -----------
@dataclass(frozen=True)
class ChannelSpec:
    name: str
    unit: str
    description: str
    steady: float           # nominal steady-state value
    noise_sigma: float      # gaussian noise sigma
    throttle_factor: float  # multiplier during 'throttle' (relative to steady)
    profile: str = "combustion"  # envelope profile: combustion | tank | valve | gimbal


CHANNELS: list[ChannelSpec] = [
    # --- original 10 channels (unchanged) ---
    ChannelSpec("pc_chamber",    "bar",   "combustion-chamber pressure",          90.0,  0.6,  0.78),
    ChannelSpec("pc_nozzle",     "bar",   "nozzle exit pressure",                 10.0,  0.15, 0.78),
    ChannelSpec("rpm_fuel_pump", "rpm",   "fuel turbopump speed",              85000.0,200.0,  0.85),
    ChannelSpec("rpm_lox_pump",  "rpm",   "LOX turbopump speed",               62000.0,150.0,  0.85),
    ChannelSpec("mdot_fuel",     "kg/s",  "fuel mass flow",                        2.0,  0.02, 0.80),
    ChannelSpec("mdot_lox",      "kg/s",  "LOX mass flow",                         6.8,  0.05, 0.80),
    ChannelSpec("tc_chamber",    "K",     "combustion-chamber temperature",     3300.0, 12.0,  0.92),
    ChannelSpec("vib_fuel_pump", "g_rms", "fuel turbopump vibration",              2.4,  0.25, 1.05),
    ChannelSpec("vib_lox_pump",  "g_rms", "LOX turbopump vibration",               2.0,  0.20, 1.05),
    ChannelSpec("t_coolant_out", "K",     "regen-cooling outlet temperature",    520.0,  3.0,  0.94),
    # --- 15 new channels ---
    ChannelSpec("p_inj_fuel",    "bar",    "fuel injector manifold pressure",       95.0,  0.8,  0.78,  "combustion"),
    ChannelSpec("p_inj_lox",     "bar",    "LOX injector manifold pressure",        98.0,  0.9,  0.78,  "combustion"),
    ChannelSpec("p_tank_fuel",   "bar",    "fuel tank ullage pressure",              4.5,  0.04, 0.98,  "tank"),
    ChannelSpec("p_tank_lox",    "bar",    "LOX tank ullage pressure",               4.2,  0.03, 0.98,  "tank"),
    ChannelSpec("tc_nozzle",     "K",      "nozzle throat temperature",           2200.0, 15.0,  0.92,  "combustion"),
    ChannelSpec("tc_injector",   "K",      "injector face temperature",            460.0,  4.0,  0.94,  "combustion"),
    ChannelSpec("t_coolant_in",  "K",      "regen-cooling inlet temperature",      115.0,  1.2,  0.98,  "combustion"),
    ChannelSpec("t_lox_inlet",   "K",      "LOX inlet temperature",                 92.0,  0.7,  0.99,  "combustion"),
    ChannelSpec("thrust_kn",     "kN",     "measured thrust",                        25.0,  0.3,  0.78,  "combustion"),
    ChannelSpec("valve_fuel",    "pct",    "fuel main valve position",             100.0,  0.3,  0.78,  "valve"),
    ChannelSpec("valve_lox",     "pct",    "LOX main valve position",              100.0,  0.3,  0.78,  "valve"),
    ChannelSpec("vib_chamber",   "g_rms",  "combustion-chamber vibration",           3.5,  0.4,  1.05,  "combustion"),
    ChannelSpec("strain_nozzle", "ustrain","nozzle throat hoop strain",            450.0,  7.0,  0.78,  "combustion"),
    ChannelSpec("acc_gimbal_x",  "g",      "gimbal actuator x-axis acceleration",    0.05,  0.08, 1.0,   "gimbal"),
    ChannelSpec("acc_gimbal_y",  "g",      "gimbal actuator y-axis acceleration",    0.04,  0.07, 1.0,   "gimbal"),
]

CHANNEL_BY_NAME = {c.name: c for c in CHANNELS}


def _phase_at(t: float) -> str:
    for name, lo, hi in PHASES:
        if lo <= t < hi:
            return name
    return PHASES[-1][0]


def _baseline_envelope(spec: ChannelSpec, t: np.ndarray) -> np.ndarray:
    """A smooth envelope that follows the canonical 7-phase burn shape.

    Profile dispatch:
      - "combustion" (default): resting ~0 for active sensors, ~300 K for
        thermal channels; tanh ramp_up; throttle dip; smooth shutdown.
      - "tank": pre-pressurised (0.85 * steady at rest); gentle monotone
        decrease from steady at ignition to 0.97 * steady at purge end.
      - "valve": resting at 0; linear ignition open 0→20%; tanh ramp 20→100%;
        steady_state at 100%; throttle at throttle_factor; linear shutdown to 0;
        purge decays to 0.
      - "gimbal": near-zero throughout; small sinusoidal flutter during
        ramp_up and steady_state phases.
    """
    out = np.zeros_like(t)

    if spec.profile == "valve":
        out[:] = 0.0
        for name, lo, hi in PHASES:
            m = (t >= lo) & (t < hi)
            if not np.any(m):
                continue
            tt = t[m]
            frac = (tt - lo) / max(hi - lo, 1e-9)
            if name == "precool":
                out[m] = 0.0
            elif name == "ignition":
                # linear 0 → 20%
                out[m] = frac * 20.0
            elif name == "ramp_up":
                # tanh-shaped smooth rise from 20% → 100%
                shaped = 0.20 + 0.80 * (np.tanh(4.0 * (frac - 0.4)) * 0.5 + 0.5)
                out[m] = shaped * spec.steady
            elif name == "steady_state":
                out[m] = spec.steady
            elif name == "throttle":
                out[m] = spec.steady * spec.throttle_factor
            elif name == "shutdown":
                start_val = spec.steady * spec.throttle_factor
                out[m] = start_val + frac * (0.0 - start_val)
            elif name == "purge":
                out[m] = 5.0 * np.exp(-3.0 * frac)
        return out

    if spec.profile == "tank":
        rest = spec.steady * 0.85
        out[:] = rest
        # Burn spans ignition start (t=2) to purge end (t=30): 28 s linear drop.
        burn_start = PHASES[1][1]   # ignition lo = 2.0
        burn_end   = PHASES[-1][2]  # purge hi  = 30.0
        span = burn_end - burn_start
        # Pre-burn: pre-pressurised rest.
        pre_mask = t < burn_start
        out[pre_mask] = rest
        # Burn window: gentle linear decrease from steady → 0.97 * steady.
        burn_mask = t >= burn_start
        burn_frac = (t[burn_mask] - burn_start) / span
        out[burn_mask] = spec.steady - (spec.steady - spec.steady * 0.97) * burn_frac
        return out

    if spec.profile == "gimbal":
        out[:] = 0.0
        # Small sinusoidal flutter during ramp_up and steady_state.
        flutter_mask = (t >= PHASES[2][1]) & (t < PHASES[3][2])  # [3,22)
        out[flutter_mask] = 0.02 * np.sin(2.0 * np.pi * 0.8 * t[flutter_mask])
        return out

    # --- "combustion" profile (existing logic, unchanged) ---
    is_thermal = spec.unit == "K"
    rest = 300.0 if is_thermal else 0.0
    out[:] = rest
    for name, lo, hi in PHASES:
        m = (t >= lo) & (t < hi)
        if not np.any(m):
            continue
        tt = t[m]
        if name == "precool":
            out[m] = rest
        elif name == "ignition":
            # short ramp from rest to ~30 % of steady (or +200 K thermal)
            frac = (tt - lo) / max(hi - lo, 1e-9)
            target = rest + 0.30 * (spec.steady - rest)
            out[m] = rest + frac * (target - rest)
        elif name == "ramp_up":
            frac = (tt - lo) / max(hi - lo, 1e-9)
            # tanh-style smooth rise from ~30 % to 100 %
            shaped = 0.30 + 0.70 * (np.tanh(4.0 * (frac - 0.4)) * 0.5 + 0.5)
            out[m] = rest + shaped * (spec.steady - rest)
        elif name == "steady_state":
            out[m] = spec.steady
        elif name == "throttle":
            out[m] = spec.steady * spec.throttle_factor + (rest * (1 - spec.throttle_factor) if is_thermal else 0.0)
            # for thermal: mild dip; for active: dip via factor
            if is_thermal:
                out[m] = rest + (spec.steady - rest) * spec.throttle_factor
        elif name == "shutdown":
            frac = (tt - lo) / max(hi - lo, 1e-9)
            start_val = spec.steady * spec.throttle_factor
            if is_thermal:
                start_val = rest + (spec.steady - rest) * spec.throttle_factor
            end_val = rest
            out[m] = start_val + frac * (end_val - start_val)
        elif name == "purge":
            # near-rest with a slow decay tail
            frac = (tt - lo) / max(hi - lo, 1e-9)
            out[m] = rest + (0.05 * (spec.steady - rest)) * np.exp(-3.0 * frac)
    return out


def _inject_anomaly(
    spec: ChannelSpec, run_idx: int, t: np.ndarray, baseline: np.ndarray, rng: np.random.Generator
) -> np.ndarray:
    """TR-004 fuel-turbopump vibration spike: 0.5 s sustained, peak 12 g rms,
    centred at t = 8 s (mid ramp_up). Returns the modified baseline (in place
    style — caller passes a fresh copy)."""
    if spec.name != "vib_fuel_pump":
        return baseline
    if run_idx != 4:  # TR-004 (1-indexed)
        return baseline
    # Plateau-shaped event: rises into 12 g, holds ~0.5 s, decays back.
    t0, t1 = 7.75, 8.30
    envelope = np.zeros_like(t)
    rise = (t >= 7.65) & (t < t0)
    plateau = (t >= t0) & (t < t1)
    decay = (t >= t1) & (t < 8.45)
    envelope[rise] = (t[rise] - 7.65) / (t0 - 7.65) * 9.6  # rise to +9.6 over baseline
    envelope[plateau] = 9.6
    envelope[decay] = 9.6 * (1.0 - (t[decay] - t1) / (8.45 - t1))
    # small jitter on top of the plateau so peak hovers near 12.x g
    envelope = envelope + rng.normal(0.0, 0.15, size=envelope.shape) * (envelope > 0)
    return baseline + envelope


def _generate_channel(
    spec: ChannelSpec, run_idx: int, rng: np.random.Generator
) -> tuple[np.ndarray, np.ndarray]:
    """Return (t_seconds, value_array) for one (run, channel)."""
    t = np.arange(N_SAMPLES, dtype=np.float64) / float(SAMPLE_RATE_HZ)
    base = _baseline_envelope(spec, t)
    noise = rng.normal(0.0, spec.noise_sigma, size=t.shape)
    # During precool & purge, dampen noise slightly so the envelope isn't dominated by it.
    # PHASES[0] is precool, PHASES[-1] is purge; use their start/end seconds.
    quiet_mask = (t < PHASES[0][2]) | (t >= PHASES[-1][1])
    noise[quiet_mask] *= 0.4
    val = base + noise
    val = _inject_anomaly(spec, run_idx, t, val, rng)
    # TR-006 has a noticeably-clean fuel-pump vibration trace (post-bearing-replacement)
    if spec.name == "vib_fuel_pump" and run_idx == 6:
        # Slightly tighter envelope: cap noise and trim baseline slightly.
        val = base * 0.95 + rng.normal(0.0, spec.noise_sigma * 0.6, size=t.shape)
    # Clip by unit
    if spec.unit in ("bar", "kg/s", "rpm", "g_rms"):
        val = np.clip(val, 0.0, None)
    if spec.unit == "pct":
        val = np.clip(val, 0.0, 100.0)
    if spec.unit == "kN":
        val = np.clip(val, 0.0, None)
    return t, val


def _write_channel_csv(path: Path, t: np.ndarray, val: np.ndarray, spec: ChannelSpec, run_t0_ns: int) -> int:
    """Write a 2-column CSV: timestamp_ns, value.

    timestamp_ns is `run_t0_ns + t * 1e9` (int) — consumable by shepard's
    timeseries POST endpoint. The seconds offset is recoverable as
    `(timestamp_ns - run_t0_ns) / 1e9`. Returns row count."""
    row_count = 0
    # Pre-compute integer timestamps once so the CSV is bit-for-bit reproducible.
    ts_ns = (run_t0_ns + (t * 1e9).astype(np.int64)).tolist()
    # Choose value precision per-unit to keep file size compact without losing meaning.
    if spec.unit in ("rpm",):
        fmt = "{:.0f}"
    elif spec.unit in ("K",):
        fmt = "{:.2f}"
    else:
        fmt = "{:.3f}"
    with path.open("w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["timestamp_ns", f"value_{spec.unit}"])
        for ns, v in zip(ts_ns, val.tolist(), strict=True):
            w.writerow([ns, fmt.format(v)])
            row_count += 1
    return row_count


def _run_t0_ns(run_idx: int) -> int:
    """Fixed launch timestamp per run (deterministic)."""
    dt = CAMPAIGN_START_DATE + timedelta(days=(run_idx - 1) * 3)
    return int(dt.timestamp() * 1e9)


def _write_runlog(path: Path, run_idx: int) -> None:
    """Operator's structured run-log entry for run `run_idx`."""
    notes = {
        1: "Bench commissioning fire. All sensors green. Mixture ratio close to target.",
        2: "Repeat reference fire. Vibration envelopes nominal across both pumps.",
        3: "Reference fire pre-anomaly. Fuel turbopump vibration trending +0.4 g rms vs TR-001 (within envelope).",
        4: "Vibration spike on fuel turbopump observed during ramp_up at t=8 s, sustained ~0.5 s peaking 12 g rms. Engine completed steady_state nominally; suspect bearing precursor. Recommending teardown.",
        5: "Hold day. Bearing teardown / replacement / re-balance. No fire.",
        6: "Re-test post bearing replacement. Fuel-pump vibration nominal across the full burn (peak ~3.6 g rms ramp_up, <2.4 g steady_state).",
        7: "Confirmation fire. Steady envelopes match TR-001/TR-002. Campaign complete.",
        8:  "Phase 2 commissioning fire. All sensors nominal. Baseline re-established after Phase 1.",
        9:  "Mixture ratio sweep — LOX/LCH4 o/f ratio stepped to 3.6 during steady_state throttle point.",
        10: "Throttle-deep test — minimum throttle point sustained 6 s at 40% thrust. Stable combustion.",
        11: "New LOX batch LOX-2024-04 validated. All channels nominal, pressures within 0.5% of Phase 1 baseline.",
        12: "Hold day. Pre-certification inspection: nozzle throat survey, injector dye-penetrant check. No anomalies.",
        13: "Pre-certification reference fire. All channels within Phase 1 envelope. Cleared for qualification.",
        14: "Qualification fire 1. Full test matrix executed. Strain gauges and gimbal nominal throughout.",
        15: "Qualification fire 2 — repeat confirmation. Campaign complete. All channels within spec.",
    }
    weather = {
        1:  {"temp_c": 24, "wind_kmh": 6,  "humidity_pct": 52, "cloud": "FEW"},
        2:  {"temp_c": 22, "wind_kmh": 9,  "humidity_pct": 60, "cloud": "SCT"},
        3:  {"temp_c": 26, "wind_kmh": 4,  "humidity_pct": 48, "cloud": "FEW"},
        4:  {"temp_c": 28, "wind_kmh": 7,  "humidity_pct": 41, "cloud": "FEW"},
        5:  {"temp_c": 30, "wind_kmh": 11, "humidity_pct": 38, "cloud": "BKN"},
        6:  {"temp_c": 25, "wind_kmh": 5,  "humidity_pct": 55, "cloud": "SCT"},
        7:  {"temp_c": 23, "wind_kmh": 8,  "humidity_pct": 58, "cloud": "OVC"},
        8:  {"temp_c": 21, "wind_kmh": 7,  "humidity_pct": 63, "cloud": "OVC"},
        9:  {"temp_c": 19, "wind_kmh": 12, "humidity_pct": 71, "cloud": "BKN"},
        10: {"temp_c": 22, "wind_kmh": 5,  "humidity_pct": 58, "cloud": "FEW"},
        11: {"temp_c": 24, "wind_kmh": 9,  "humidity_pct": 50, "cloud": "SCT"},
        12: {"temp_c": 26, "wind_kmh": 6,  "humidity_pct": 44, "cloud": "FEW"},
        13: {"temp_c": 25, "wind_kmh": 8,  "humidity_pct": 47, "cloud": "SCT"},
        14: {"temp_c": 23, "wind_kmh": 10, "humidity_pct": 53, "cloud": "OVC"},
        15: {"temp_c": 20, "wind_kmh": 7,  "humidity_pct": 67, "cloud": "BKN"},
    }
    if run_idx in {5, 12}:
        igniter = None
        is_fired = False
    else:
        igniter = "PASS"
        is_fired = True
    entry = {
        "test_run_id": f"TR-{run_idx:03d}",
        "campaign": "lumen-inspired-q3-2024",
        "bench": BENCH,
        "propellant": PROPELLANT,
        "scheduled_at": (CAMPAIGN_START_DATE + timedelta(days=(run_idx - 1) * 3)).isoformat(),
        "shift": "early" if run_idx % 2 else "late",
        "test_engineer": TEST_ENGINEERS[run_idx - 1],
        "is_fired": is_fired,
        "duration_s": DURATION_S if is_fired else 0,
        "target_thrust_kN": TARGET_THRUST_KN,
        "target_mixture_ratio": TARGET_MIXTURE_RATIO,
        "lox_lot_id": f"LOX-2024-{(run_idx - 1) // 3 + 1:02d}",
        "lch4_lot_id": f"CH4-2024-{(run_idx - 1) // 2 + 1:02d}",
        "weather": weather[run_idx],
        "igniter_check": igniter,
        "notes_brief": notes[run_idx],
    }
    path.write_text(json.dumps(entry, indent=2) + "\n", encoding="utf-8")


SCHEMA: dict = {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "title": "LUMEN-Inspired Hot-Fire Run Log (synthetic)",
    "description": "Operator's structured run-log entry per test run. Synthetic schema sketch — not a DLR standard.",
    "type": "object",
    "required": [
        "test_run_id", "campaign", "bench", "propellant",
        "scheduled_at", "shift", "test_engineer",
        "is_fired", "duration_s",
        "target_thrust_kN", "target_mixture_ratio",
        "lox_lot_id", "lch4_lot_id",
        "weather", "igniter_check", "notes_brief",
    ],
    "properties": {
        "test_run_id":        {"type": "string", "pattern": "^TR-[0-9]{3}$"},
        "campaign":           {"type": "string"},
        "bench":              {"type": "string"},
        "propellant":         {"type": "string"},
        "scheduled_at":       {"type": "string", "format": "date-time"},
        "shift":              {"type": "string", "enum": ["early", "late"]},
        "test_engineer":      {"type": "string"},
        "is_fired":           {"type": "boolean"},
        "duration_s":         {"type": "integer", "minimum": 0},
        "target_thrust_kN":   {"type": "number"},
        "target_mixture_ratio": {"type": "number"},
        "lox_lot_id":         {"type": "string"},
        "lch4_lot_id":        {"type": "string"},
        "weather": {
            "type": "object",
            "required": ["temp_c", "wind_kmh", "humidity_pct", "cloud"],
            "properties": {
                "temp_c":       {"type": "number"},
                "wind_kmh":     {"type": "number"},
                "humidity_pct": {"type": "number"},
                "cloud":        {"type": "string"},
            },
        },
        "igniter_check": {"type": ["string", "null"], "enum": ["PASS", "FAIL", None]},
        "notes_brief":   {"type": "string"},
    },
}


def _write_test_report(path: Path, run_idx: int) -> None:
    if run_idx == 5:
        fired_block = "**Fired:** no — hold day for bearing teardown.\n"
    elif run_idx == 12:
        fired_block = "**Fired:** no — hold day for pre-certification inspection.\n"
    else:
        fired_block = (
            "**Fired:** yes\n\n"
            f"Burn duration: {DURATION_S} s. Phases executed: precool / ignition / "
            "ramp_up / steady_state / throttle / shutdown / purge.\n"
        )
    extra = ""
    if run_idx == 4:
        extra = (
            "\n## Anomaly\n\n"
            "A vibration excursion on `vib_fuel_pump` was recorded mid ramp_up "
            "(t = 8.0 s, sustained 0.5 s, peak ~12 g rms). The engine continued "
            "to steady_state nominally; the event is flagged for investigation "
            "(see *Anomaly Investigation — TR-004 Fuel Turbopump*).\n"
        )
    if run_idx == 6:
        extra = (
            "\n## Re-test outcome\n\n"
            "Post-bearing-replacement re-fire. `vib_fuel_pump` nominal across "
            "the full burn (peak ~3.6 g rms ramp_up; ≤ 2.4 g rms steady_state). "
            "Bearing replacement deemed effective.\n"
        )
    if run_idx == 8:
        extra = (
            "\n## Phase 2 commissioning\n\n"
            "Phase 2 campaign opened with a full reference fire. All 25 channels "
            "nominal. Baseline re-established and compared to Phase 1 envelope; "
            "no statistically significant drift observed.\n"
        )
    if run_idx == 9:
        extra = (
            "\n## Mixture ratio sweep\n\n"
            "LOX/LCH4 o/f ratio stepped from 3.4 to 3.6 during the steady_state "
            "throttle point. `mdot_lox` increased ~5%; combustion chamber pressure "
            "and temperature responded within expected bounds.\n"
        )
    if run_idx == 10:
        extra = (
            "\n## Deep-throttle test\n\n"
            "Minimum throttle point (40% thrust) sustained for 6 s during the "
            "throttle phase. Combustion remained stable throughout; no oscillation "
            "detected on `pc_chamber` or `vib_chamber`.\n"
        )
    if run_idx == 11:
        extra = (
            "\n## New LOX batch validation\n\n"
            "LOX batch LOX-2024-04 introduced. Injector manifold pressures and "
            "mass flow ratios all within 0.5% of Phase 1 baseline.\n"
        )
    if run_idx == 12:
        extra = (
            "\n## Inspection findings\n\n"
            "Nozzle throat survey: throat diameter within drawing tolerance. "
            "Injector face dye-penetrant inspection: no indications. Cleared "
            "for pre-certification reference fire TR-013.\n"
        )
    if run_idx == 13:
        extra = (
            "\n## Pre-certification clearance\n\n"
            "All channels within Phase 1 envelope. Strain gauges and gimbal "
            "accelerometers nominal. Engine cleared for qualification firing.\n"
        )
    if run_idx == 14:
        extra = (
            "\n## Qualification fire 1\n\n"
            "Full qualification test matrix executed. `strain_nozzle` and "
            "`acc_gimbal_x`/`acc_gimbal_y` nominal throughout. No anomalies.\n"
        )
    if run_idx == 15:
        extra = (
            "\n## Qualification fire 2 — campaign close\n\n"
            "Repeat confirmation of qualification fire. All 25 channels within "
            "spec. Campaign formally complete.\n"
        )
    body = (
        f"# TR-{run_idx:03d} test report (synthetic)\n\n"
        f"_This report is synthetic placeholder content for the LUMEN-inspired "
        f"shepard showcase. Not a real DLR document._\n\n"
        f"- Bench: `{BENCH}`\n"
        f"- Propellant: `{PROPELLANT}`\n"
        f"- Target thrust: {TARGET_THRUST_KN} kN\n"
        f"- Target mixture ratio: {TARGET_MIXTURE_RATIO}\n"
        f"- Test engineer: {TEST_ENGINEERS[run_idx - 1]}\n\n"
        f"{fired_block}"
        f"{extra}"
    )
    path.write_text(body, encoding="utf-8")


def _write_cad_stub(path: Path, run_idx: int) -> None:
    """Deterministic 4 KB binary placeholder labelled as an engine drawing.

    The first 256 bytes are a human-readable header so a casual reader sees
    that this is intentionally a placeholder."""
    header = (
        f"SHEPARD-SHOWCASE-PLACEHOLDER\n"
        f"role: cad_assembly_drawing_stub\n"
        f"run: TR-{run_idx:03d}\n"
        f"note: Synthetic placeholder. No real engineering content.\n"
    ).encode("utf-8")
    body = bytearray(4096)
    body[: len(header)] = header
    # Deterministic filler: repeating low-entropy pattern keyed by run index.
    pattern = bytes(((i * 31 + run_idx * 7) & 0xFF for i in range(256)))
    for off in range(len(header), 4096, 256):
        end = min(off + 256, 4096)
        body[off:end] = pattern[: end - off]
    path.write_bytes(bytes(body))


def _write_anomaly_photo_stub(path: Path) -> None:
    header = (
        b"SHEPARD-SHOWCASE-PLACEHOLDER\n"
        b"role: anomaly_inspection_photo_stub\n"
        b"note: Synthetic placeholder for the bearing-spalling photo "
        b"referenced in the TR-004 investigation.\n"
    )
    body = bytearray(4096)
    body[: len(header)] = header
    pattern = bytes(((i * 13 + 41) & 0xFF for i in range(256)))
    for off in range(len(header), 4096, 256):
        end = min(off + 256, 4096)
        body[off:end] = pattern[: end - off]
    path.write_bytes(bytes(body))


def _write_png(path: Path, rgb: np.ndarray) -> None:
    """Write a (H, W, 3) uint8 numpy array as a PNG file (stdlib only)."""
    h, w = rgb.shape[:2]

    def _chunk(name: bytes, data: bytes) -> bytes:
        body = name + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    raw = b"".join(b'\x00' + bytes(rgb[y].tobytes()) for y in range(h))
    path.write_bytes(
        b'\x89PNG\r\n\x1a\n'
        + _chunk(b'IHDR', struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
        + _chunk(b'IDAT', zlib.compress(raw, 6))
        + _chunk(b'IEND', b'')
    )


def _write_thermal_image(path: Path, run_idx: int, rng: np.random.Generator) -> None:
    """Generate a false-colour 'thermal profile' PNG for one test run.

    X-axis = time (0-30 s), Y-axis = normalised spatial cross-section.
    Uses the same thermal-colormap convention as the timeseries plots.
    TR-004 shows a visible hot-spot during ramp_up to mirror the vib anomaly.
    Hold days (TR-005, TR-012) get a greyscale 'no-data' pattern."""
    W, H = 320, 200

    if run_idx in {5, 12}:
        # Hold day — uniform grey with a 'NO FIRE' crosshatch pattern.
        grey = np.full((H, W, 3), 48, dtype=np.uint8)
        for y in range(0, H, 20):
            grey[y, :] = [80, 80, 80]
        for x in range(0, W, 20):
            grey[:, x] = [80, 80, 80]
        _write_png(path, grey)
        return

    t = np.linspace(0, 30, W)
    y_pos = np.linspace(0, 1, H)
    tt, yy = np.meshgrid(t, y_pos)

    # Phase envelope — mirrors the canonical 7-phase burn shape.
    phase = np.where(tt < 3, 0.0,
        np.where(tt < 9,  (tt - 3) / 6.0,
        np.where(tt < 22, 1.0,
        np.where(tt < 26, 0.78,
        np.where(tt < 28, np.maximum(0.0, 0.78 * (1 - (tt - 26) / 2)), 0.0)))))

    # Spatial Gaussian — peak at nominal nozzle centreline (y ≈ 0.5).
    y_peak = 0.5 + rng.uniform(-0.04, 0.04)
    spatial = np.exp(-((yy - y_peak) / 0.28) ** 2)

    intensity = phase * spatial + rng.normal(0, 0.025, (H, W))

    # TR-004 anomaly hot-spot: upper-left quadrant during ramp_up at t~8 s.
    if run_idx == 4:
        hotspot = ((tt >= 7.5) & (tt <= 9.0)) & ((yy >= 0.62) & (yy <= 0.88))
        intensity = np.where(hotspot, intensity + rng.uniform(0.35, 0.55, (H, W)), intensity)

    intensity = np.clip(intensity, 0, 1)

    # Thermal colormap: black→deep-blue→red→orange→yellow.
    r = np.clip(intensity * 3.5 - 1.0, 0, 1)
    g = np.clip(intensity * 3.5 - 2.2, 0, 1) * 0.65
    b = np.clip((1 - intensity) * 2.0, 0, 1) * 0.8

    rgb = (np.stack([r, g, b], axis=2) * 255).astype(np.uint8)
    _write_png(path, rgb)


def generate(out_dir: Path) -> dict:
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "timeseries").mkdir(exist_ok=True)
    (out_dir / "structured").mkdir(exist_ok=True)
    (out_dir / "files").mkdir(exist_ok=True)

    rng = np.random.default_rng(RNG_SEED)
    manifest: dict = {
        "rng_seed": RNG_SEED,
        "sample_rate_hz": SAMPLE_RATE_HZ,
        "duration_s": DURATION_S,
        "samples_per_channel": N_SAMPLES,
        "n_runs": N_RUNS,
        "channels": [c.name for c in CHANNELS],
        "phases": [{"name": n, "start_s": lo, "end_s": hi} for n, lo, hi in PHASES],
        "runs": [],
    }
    anomaly_summary: dict | None = None

    for run_idx in range(1, N_RUNS + 1):
        run_t0_ns = _run_t0_ns(run_idx)
        # Skip TR-005 (bearing teardown) and TR-012 (pre-certification inspection)
        is_fired = run_idx not in {5, 12}
        run_record = {
            "test_run_id": f"TR-{run_idx:03d}",
            "is_fired": is_fired,
            "t0_ns": run_t0_ns,
            "channels": [],
        }
        if is_fired:
            for spec in CHANNELS:
                t, val = _generate_channel(spec, run_idx, rng)
                csv_path = out_dir / "timeseries" / f"tr-{run_idx:03d}-{spec.name}.csv"
                rows = _write_channel_csv(csv_path, t, val, spec, run_t0_ns)
                run_record["channels"].append(
                    {
                        "name": spec.name,
                        "unit": spec.unit,
                        "rows": rows,
                        "min": float(np.min(val)),
                        "max": float(np.max(val)),
                        "mean": float(np.mean(val)),
                        "csv": str(csv_path.relative_to(out_dir)),
                    }
                )
                # Capture the anomaly summary for the manifest.
                if spec.name == "vib_fuel_pump" and run_idx == 4:
                    peak_idx = int(np.argmax(val))
                    anomaly_summary = {
                        "run": "TR-004",
                        "channel": "vib_fuel_pump",
                        "peak_value_g_rms": round(float(val[peak_idx]), 3),
                        "peak_t_seconds": round(float(t[peak_idx]), 3),
                        "duration_s_above_8g": round(
                            float(np.sum(val > 8.0)) / SAMPLE_RATE_HZ, 3
                        ),
                    }
        # Structured run-log + report + CAD stub + thermal image for every run.
        _write_runlog(out_dir / "structured" / f"tr-{run_idx:03d}-runlog.json", run_idx)
        _write_test_report(out_dir / "files" / f"tr-{run_idx:03d}-test-report.md", run_idx)
        _write_cad_stub(out_dir / "files" / f"tr-{run_idx:03d}-cad-stub.bin", run_idx)
        _write_thermal_image(out_dir / "files" / f"tr-{run_idx:03d}-thermal.png", run_idx, rng)
        manifest["runs"].append(run_record)

    # Schema and the anomaly investigation photo stub.
    (out_dir / "structured" / "schema.json").write_text(
        json.dumps(SCHEMA, indent=2) + "\n", encoding="utf-8"
    )
    _write_anomaly_photo_stub(out_dir / "files" / "anomaly-photo-stub.bin")
    manifest["anomaly_summary"] = anomaly_summary

    (out_dir / "manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    return manifest


def _parse_args(argv: Iterable[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument(
        "--out",
        type=Path,
        default=Path(__file__).resolve().parent,
        help="output directory (default: %(default)s)",
    )
    return p.parse_args(argv)


def main(argv: Iterable[str] | None = None) -> None:
    args = _parse_args(argv)
    manifest = generate(args.out)
    print(f"OK generated {len(manifest['runs'])} runs into {args.out}")
    if manifest.get("anomaly_summary"):
        a = manifest["anomaly_summary"]
        print(
            f"OK anomaly: {a['run']} {a['channel']} "
            f"peak {a['peak_value_g_rms']} g rms at t={a['peak_t_seconds']} s "
            f"(rng_seed={RNG_SEED})"
        )


if __name__ == "__main__":
    main()
