"""Deterministic synthetic data generator for the LUMEN-inspired showcase.

This is a *fallback* for when the sibling LUMEN agent's
``examples/seed-showcase/data/generate.py`` has not yet been committed.
The two generators MUST produce bit-for-bit identical CSVs because both
``seed.py`` (dispatcher branch) and ``import_upstream.py`` (this script)
need to import the same Collection contents into shepard.

Spec — single source of truth, do not drift:

* numpy.random.default_rng(2024)
* Seven test runs TR-001..TR-007
* Per-run timeseries fields:
    - t                  (seconds since run start, 0..60, dt=0.01 -> 6001 rows)
    - thrust_kN          (kN)
    - p_chamber_bar      (chamber pressure, bar)
    - t_throat_K         (throat temperature, kelvin)
    - vib_fuel_pump      (g, RMS, single channel)
* Phases of burn (seconds):
    - prechill   0  ..  5
    - ignition   5  .. 10
    - mainstage 10  .. 50
    - shutdown  50  .. 60
* TR-004 carries a vibration anomaly: a +6.0 g spike in vib_fuel_pump
  centred at t = 32.5 s with 0.4 s gaussian width. TR-006 is the
  re-fly: the spike does not appear (the fix held). TR-001..3, 5, 7
  are nominal.
* CSV column order is fixed:
    t,thrust_kN,p_chamber_bar,t_throat_K,vib_fuel_pump
  Numeric formatting: %.6f (six decimals).

The generator is deterministic across the 7 runs because we draw from
ONE rng instance in run order — TR-001 first, TR-007 last. Re-ordering
breaks bit-equivalence. Do not parallelise.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path

try:
    import numpy as np  # noqa: F401  -- required for rng parity with seed.py
except ImportError as exc:  # pragma: no cover
    raise SystemExit(
        "numpy is required for the data fallback (matching sibling seed.py "
        "rng spec). pip install numpy"
    ) from exc


RUN_IDS = ["TR-001", "TR-002", "TR-003", "TR-004", "TR-005", "TR-006", "TR-007"]
DT = 0.01
DURATION = 60.0  # seconds
N_SAMPLES = int(DURATION / DT) + 1  # 6001

PHASES = [
    ("prechill", 0.0, 5.0),
    ("ignition", 5.0, 10.0),
    ("mainstage", 10.0, 50.0),
    ("shutdown", 50.0, 60.0),
]

# Run-mean nominals — a campaign-style monotonic drift to give the
# notebook something to chart.
RUN_THRUST_MEAN_KN = {
    "TR-001": 24.5,
    "TR-002": 24.7,
    "TR-003": 24.6,
    "TR-004": 24.9,
    "TR-005": 25.0,
    "TR-006": 24.8,
    "TR-007": 25.1,
}


def _phase_envelope(t: float) -> float:
    """Return a 0..1 multiplier representing burn-phase amplitude."""
    if t < 5.0:
        return 0.05
    if t < 10.0:
        return (t - 5.0) / 5.0
    if t < 50.0:
        return 1.0
    if t < 60.0:
        return max(0.0, 1.0 - (t - 50.0) / 10.0)
    return 0.0


def _generate_run(rng, run_id: str) -> list[list[float]]:
    """Return a list of [t, thrust_kN, p_chamber_bar, t_throat_K, vib_fuel_pump] rows."""
    thrust_mean = RUN_THRUST_MEAN_KN[run_id]
    rows: list[list[float]] = []
    # Pre-draw the noise vectors in fixed order so the rng stream is
    # deterministic per run regardless of inner loop work.
    n_thrust = rng.normal(0.0, 0.05, N_SAMPLES)
    n_pchamber = rng.normal(0.0, 0.6, N_SAMPLES)
    n_tthroat = rng.normal(0.0, 8.0, N_SAMPLES)
    n_vib = rng.normal(0.0, 0.15, N_SAMPLES)
    for i in range(N_SAMPLES):
        t = i * DT
        env = _phase_envelope(t)
        thrust = thrust_mean * env + n_thrust[i] * env
        p_chamber = 65.0 * env + n_pchamber[i] * env  # bar
        t_throat = 300.0 + 2300.0 * env + n_tthroat[i] * env  # K
        vib = 1.2 * env + n_vib[i] * env
        if run_id == "TR-004" and 30.0 <= t <= 35.0:
            spike = 6.0 * math.exp(-((t - 32.5) ** 2) / (2 * 0.4 * 0.4))
            vib += spike
        rows.append([t, thrust, p_chamber, t_throat, vib])
    return rows


def _write_csv(path: Path, rows: list[list[float]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["t", "thrust_kN", "p_chamber_bar", "t_throat_K", "vib_fuel_pump"])
        for row in rows:
            w.writerow([f"{x:.6f}" for x in row])


def generate(data_dir: Path) -> dict[str, Path]:
    """Generate one CSV per run under ``data_dir/timeseries``.

    Returns a {run_id: csv_path} map.
    """
    np = __import__("numpy")
    rng = np.random.default_rng(2024)
    out_dir = data_dir / "timeseries"
    out_dir.mkdir(parents=True, exist_ok=True)
    out: dict[str, Path] = {}
    for run_id in RUN_IDS:
        rows = _generate_run(rng, run_id)
        path = out_dir / f"{run_id}.csv"
        _write_csv(path, rows)
        out[run_id] = path
    return out


def read_csv(path: Path) -> list[dict[str, float]]:
    """Read a generated CSV back into a list of dict rows."""
    with path.open() as fh:
        reader = csv.DictReader(fh)
        return [{k: float(v) for k, v in row.items()} for row in reader]


def phases_for(run_start_unix_ns: int) -> list[tuple[str, int, int]]:
    """Return [(phase_name, start_ns, end_ns)] for a run starting at run_start_unix_ns."""
    out = []
    for name, s, e in PHASES:
        out.append((name, run_start_unix_ns + int(s * 1e9), run_start_unix_ns + int(e * 1e9)))
    return out


if __name__ == "__main__":
    import argparse

    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument(
        "--data-dir",
        type=Path,
        default=Path(__file__).parent / "data",
        help="Output directory (will write data-dir/timeseries/TR-NNN.csv)",
    )
    args = p.parse_args()
    written = generate(args.data_dir)
    for run_id, path in written.items():
        print(f"OK wrote {run_id} -> {path}")
