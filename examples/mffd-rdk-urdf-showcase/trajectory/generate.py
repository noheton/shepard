#!/usr/bin/env python3
"""generate.py — synthetic AFP ply layup joint trajectory for the KR210 R2700/2.

Produces a 30-second, 100 Hz joint-angle trajectory (3000 samples per joint)
for the six revolute joints of the KUKA KR210 R2700/2 (kuka_quantec_support
URDF from kroshu/kuka_robot_descriptions):

    joint_1, joint_2, joint_3, joint_4, joint_5, joint_6

The motion is a believable AFP layup pass: a planar raster sweep at constant
TCP height, with the TCP traversing back-and-forth across a ~600 mm strip.
This is NOT a kinematically solved trajectory — joint angles are hand-tuned
sinusoidal + ramp combinations chosen to look like a real layup when the
URDF animator plays them back at 1x speed. The endpoint joint motion
matches the KR210 joint limits declared in the URDF.

Outputs one CSV per joint to ./, sharing the timestamp column:

    timestamp,value
    1717852800000000000,0.0
    1717852800010000000,0.0017
    ...

Timestamps are nanoseconds since epoch (Shepard convention), starting at a
fixed seed time so re-runs are bit-identical.

Usage:
    python3 generate.py        # writes 6 CSVs to .
    python3 generate.py --out /elsewhere/

The seeder (``seed.py``) reads these CSVs and uploads each as a Timeseries
channel annotated with ``urn:shepard:urdf:joint = <jointName>``.
"""
from __future__ import annotations

import argparse
import csv
import math
from pathlib import Path

# ---------------------------------------------------------------------------
# Constants

# Anchor time — bit-identical re-runs.
START_NS = 1_717_852_800_000_000_000  # 2024-06-08T12:00:00Z (an "AFP test day")

SAMPLE_HZ = 100
DURATION_S = 30
N_SAMPLES = SAMPLE_HZ * DURATION_S  # 3000

JOINT_NAMES = ["joint_1", "joint_2", "joint_3", "joint_4", "joint_5", "joint_6"]


def _joint_angle(joint: str, t: float) -> float:
    """Return radians for *joint* at time *t* seconds.

    The layup motion: TCP traces a back-and-forth raster across the
    600 mm strip every ~6 s, with a slow downstream advance over 30 s.

    joint_1 — base yaw — slow linear advance (+30° → +60°) over 30 s.
    joint_2 — shoulder pitch — small oscillation about -45° (TCP height ≈ const).
    joint_3 — elbow pitch — counter-oscillation about +90° (keeps wrist level).
    joint_4 — wrist roll — small saw-tooth (orienting the AFP head).
    joint_5 — wrist pitch — small oscillation (normal-to-surface keep-alive).
    joint_6 — tool roll — fast 0.5 Hz pass-frequency oscillation (raster sweep).
    """
    if joint == "joint_1":
        # Linear advance + tiny jitter (settling)
        return math.radians(30 + (t / DURATION_S) * 30 + 0.5 * math.sin(2 * math.pi * 0.3 * t))
    if joint == "joint_2":
        return math.radians(-45 + 3 * math.sin(2 * math.pi * 0.5 * t))
    if joint == "joint_3":
        return math.radians(90 - 3 * math.sin(2 * math.pi * 0.5 * t))
    if joint == "joint_4":
        # Saw-tooth — every 6 s reset
        phase = (t % 6.0) / 6.0
        return math.radians(-10 + 20 * phase)
    if joint == "joint_5":
        return math.radians(15 + 5 * math.sin(2 * math.pi * 0.5 * t))
    if joint == "joint_6":
        # Pass-frequency oscillation — the AFP raster sweep
        return math.radians(45 * math.sin(2 * math.pi * 0.5 * t))
    return 0.0


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--out", default=str(Path(__file__).parent), help="output directory")
    args = p.parse_args()

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    dt_ns = int(1e9 // SAMPLE_HZ)  # 10_000_000 ns per sample

    for joint in JOINT_NAMES:
        path = out_dir / f"{joint}.csv"
        with path.open("w", newline="") as f:
            w = csv.writer(f)
            w.writerow(["timestamp", "value"])
            for i in range(N_SAMPLES):
                ts_ns = START_NS + i * dt_ns
                t_s = i / SAMPLE_HZ
                w.writerow([ts_ns, f"{_joint_angle(joint, t_s):.6f}"])
        print(f"OK  {path}  ({N_SAMPLES} samples)")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
