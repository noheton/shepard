#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["requests>=2.31"]
# ///
"""
mffd-import-stats-collector.py — Shepard-measures-itself observability collector.

Polls the running MFFD import on shepard-api.nuclide.systems, computes per-payload-
kind counters (DataObject counts, file refs + total bytes, TS refs + total channels,
structured-data refs, Garage bucket size), and writes them as datapoints into a
Shepard TimeseriesContainer named `mffd-import-stats-2026-05-23` linked to a
DataObject `ImportStats-2026-05-23-mffd` in the MFFD-Dropbox collection.

This is the OBS-MFFD1 dispatcher backlog row: monitoring data lives IN Shepard,
not in a parallel observability stack. Recursive by design.

Channel convention (5-tuple):
  measurement = "import_progress"
  device      = "mffd-dropbox"
  location    = "dest"
  symbolicName = the metric name (e.g. "dos_total", "file_bytes_total")
  field       = "value"   (single-field-per-channel convention)

Timestamps: NANOSECONDS since unix epoch (per TimeseriesDataPoint.timestamp).

Modes:
  --bootstrap   Create the TimeseriesContainer + DataObject if not present.
                Prints the numeric STATS_TS_CONTAINER_ID + STATS_DO_ID to stdout.
                Does NOT create the TimeseriesReference yet (deferred to first tick;
                a TS reference requires non-empty timeseries[] — Bug A fix from v15).
  --one-tick    Run exactly one collection tick + exit (systemd oneshot model).
  (default)     Loop forever: tick, sleep 300s, repeat. SIGINT/SIGTERM cleanly exits.

Environment:
  SHEPARD_URL              default: https://shepard-api.nuclide.systems
  SHEPARD_API_KEY          required (JWT)
  COLL_APP_ID              default: 019e4e56-ca63-76f3-9bf0-6681f7fe6d56 (MFFD-Dropbox)
  STATS_TS_CONTAINER_ID    required for --one-tick / loop mode (set after --bootstrap)
  STATS_DO_ID              required for --one-tick / loop mode (set after --bootstrap)
  TICK_SECONDS             default: 300

Usage:
  # one-time bootstrap (mints the container + DO)
  SHEPARD_API_KEY=... uv run scripts/mffd-import-stats-collector.py --bootstrap

  # subsequent ticks (systemd oneshot model)
  SHEPARD_API_KEY=... STATS_TS_CONTAINER_ID=... STATS_DO_ID=... \\
      uv run scripts/mffd-import-stats-collector.py --one-tick

  # ad-hoc loop (this conversation)
  SHEPARD_API_KEY=... STATS_TS_CONTAINER_ID=... STATS_DO_ID=... \\
      uv run scripts/mffd-import-stats-collector.py

Authority: this script writes to the LIVE nuclide instance. It only POSTs new
datapoints and creates one container + one DataObject; it does not delete or
mutate existing data.
"""
from __future__ import annotations

import argparse
import os
import re
import signal
import subprocess
import sys
import time
from dataclasses import dataclass, field
from typing import Any

import requests

# ── Configuration ───────────────────────────────────────────────────────────────
DEFAULT_URL = "https://shepard-api.nuclide.systems"
DEFAULT_COLL_APP_ID = "019e4e56-ca63-76f3-9bf0-6681f7fe6d56"
TS_CONTAINER_NAME = "mffd-import-stats-2026-05-23"
STATS_DO_NAME = "ImportStats-2026-05-23-mffd"
TS_REF_NAME = "import-progress-counters"

MEASUREMENT = "import_progress"
DEVICE = "mffd-dropbox"
LOCATION = "dest"
FIELD = "value"

# Name-pattern categorisation, matching v15's --verify-imported pattern (substring,
# case-insensitive). Order matters: first match wins.
DO_NAME_PATTERNS: list[tuple[str, str]] = [
    ("tapelaying", "dos_tapelaying"),
    ("bridgewelding", "dos_bridgewelding"),
    ("skeleton", "dos_skeleton"),
]

# Channel set we emit on every tick.
METRIC_CHANNELS = [
    "dos_total",
    "dos_tapelaying",
    "dos_bridgewelding",
    "dos_skeleton",
    "dos_other",
    "file_refs_count",
    "file_bytes_total",
    "ts_refs_count",
    "ts_channels_total",
    "sd_refs_count",
    "garage_bucket_bytes",
]


# ── Shutdown flag ───────────────────────────────────────────────────────────────
_STOP = False


def _stop_handler(signum: int, _frame: Any) -> None:  # noqa: ARG001
    global _STOP
    _STOP = True
    print(f"[signal] received {signum}, shutting down after current tick", flush=True)


# ── Shepard client (thin) ───────────────────────────────────────────────────────
@dataclass
class Shepard:
    base: str
    api_key: str

    def _headers(self) -> dict[str, str]:
        return {"X-API-KEY": self.api_key, "Accept": "application/json"}

    def get(self, path: str, params: dict[str, Any] | None = None) -> Any:
        r = requests.get(f"{self.base}{path}", headers=self._headers(), params=params, timeout=60)
        r.raise_for_status()
        return r.json() if r.text else None

    def post(self, path: str, body: dict) -> Any:
        r = requests.post(
            f"{self.base}{path}",
            headers={**self._headers(), "Content-Type": "application/json"},
            json=body,
            timeout=60,
        )
        if not r.ok:
            print(f"[http] POST {path} -> {r.status_code}: {r.text[:300]}", flush=True)
        r.raise_for_status()
        return r.json() if r.text else None


# ── Counter aggregation ─────────────────────────────────────────────────────────
@dataclass
class Tick:
    timestamp_ns: int
    counters: dict[str, float] = field(default_factory=dict)

    def set(self, name: str, value: float) -> None:
        self.counters[name] = float(value)

    def summary(self) -> str:
        return (
            f"DO={int(self.counters.get('dos_total', 0))} "
            f"(tape={int(self.counters.get('dos_tapelaying', 0))}, "
            f"bridge={int(self.counters.get('dos_bridgewelding', 0))}, "
            f"skel={int(self.counters.get('dos_skeleton', 0))}, "
            f"other={int(self.counters.get('dos_other', 0))}) "
            f"files={int(self.counters.get('file_refs_count', 0))} "
            f"bytes={int(self.counters.get('file_bytes_total', 0))} "
            f"TSrefs={int(self.counters.get('ts_refs_count', 0))} "
            f"channels={int(self.counters.get('ts_channels_total', 0))} "
            f"SDrefs={int(self.counters.get('sd_refs_count', 0))} "
            f"garage={int(self.counters.get('garage_bucket_bytes', 0))}"
        )


def _list_dataobjects(sh: Shepard, coll_app_id: str) -> list[dict]:
    """Paginate /v2/collections/{appId}/data-objects until empty (no X-Total-Count)."""
    out: list[dict] = []
    page = 0
    size = 200
    while True:
        rows = sh.get(
            f"/v2/collections/{coll_app_id}/data-objects",
            params={"page": page, "size": size},
        )
        if not rows:
            break
        out.extend(rows)
        if len(rows) < size:
            break
        page += 1
    return out


def _get_v1_do(sh: Shepard, coll_id: int, do_id: int) -> dict:
    return sh.get(f"/shepard/api/collections/{coll_id}/dataObjects/{do_id}")


def _get_v1_ref(sh: Shepard, coll_id: int, do_id: int, ref_id: int, kind: str) -> dict | None:
    """kind ∈ {fileReferences, timeseriesReferences, structuredDataReferences}."""
    try:
        return sh.get(
            f"/shepard/api/collections/{coll_id}/dataObjects/{do_id}/{kind}/{ref_id}"
        )
    except requests.HTTPError as exc:
        if exc.response is not None and exc.response.status_code == 404:
            return None
        raise


def _get_v1_file(sh: Shepard, file_container_id: int, file_id: int) -> dict | None:
    try:
        return sh.get(f"/shepard/api/fileContainers/{file_container_id}/files/{file_id}")
    except requests.HTTPError as exc:
        if exc.response is not None and exc.response.status_code == 404:
            return None
        raise


def _categorise(do_name: str) -> str:
    """Returns the metric channel name for this DO's category."""
    lname = (do_name or "").lower()
    for needle, channel in DO_NAME_PATTERNS:
        if needle in lname:
            return channel
    return "dos_other"


def _garage_bucket_bytes() -> int:
    """Run `docker exec shepard-garage /garage bucket info shepard-files` and parse the
    'Size: X kiB (Y bytes)' line. Returns bytes; 0 on failure (logged, not raised).

    The distroless garage image has only the /garage binary — no du, no sh.
    """
    try:
        out = subprocess.check_output(
            ["docker", "exec", "shepard-garage", "/garage", "bucket", "info", "shepard-files"],
            stderr=subprocess.STDOUT,
            timeout=30,
        ).decode("utf-8", errors="replace")
    except Exception as exc:  # noqa: BLE001
        print(f"[garage] failed: {exc}", flush=True)
        return 0
    # Lines like:  "Size: 156.5 kiB (160.3 KB)"
    # Both figures are decimal-formatted; we want a byte count. Parse the
    # parenthetical (KB/MB/GB/TB or B) and convert.
    m = re.search(r"Size:\s+[\d.]+\s+\S+\s+\(([\d.]+)\s*(B|KB|MB|GB|TB)\)", out)
    if m:
        value, unit = float(m.group(1)), m.group(2)
        scale = {"B": 1, "KB": 1_000, "MB": 1_000_000, "GB": 1_000_000_000, "TB": 1_000_000_000_000}[unit]
        return int(value * scale)
    # Fallback: try a kiB / MiB / GiB second-figure pattern (older garage).
    m = re.search(r"Size:\s+([\d.]+)\s*(B|KiB|MiB|GiB|TiB)", out)
    if m:
        value, unit = float(m.group(1)), m.group(2)
        scale = {"B": 1, "KiB": 1024, "MiB": 1024**2, "GiB": 1024**3, "TiB": 1024**4}[unit]
        return int(value * scale)
    print(f"[garage] could not parse Size from output:\n{out[:300]}", flush=True)
    return 0


# ── Tick ────────────────────────────────────────────────────────────────────────
def collect_tick(sh: Shepard, coll_app_id: str) -> Tick:
    """One full data-influx survey of the MFFD-Dropbox collection."""
    tick = Tick(timestamp_ns=time.time_ns())
    # Zero-init so every metric channel is emitted every tick (clean line chart).
    for ch in METRIC_CHANNELS:
        tick.set(ch, 0)

    dos = _list_dataobjects(sh, coll_app_id)
    tick.set("dos_total", len(dos))

    file_refs = 0
    file_bytes = 0
    ts_refs = 0
    ts_channels = 0
    sd_refs = 0

    for do in dos:
        # Categorise by name pattern.
        cat = _categorise(do.get("name", ""))
        tick.counters[cat] = tick.counters.get(cat, 0) + 1

        coll_id = do.get("collectionId")
        do_id = do.get("id")
        if coll_id is None or do_id is None:
            continue

        # Fetch the v1 DO body to walk reference families.
        try:
            v1do = _get_v1_do(sh, coll_id, do_id)
        except requests.HTTPError as exc:
            print(f"[do] {do_id}: v1 fetch failed: {exc}", flush=True)
            continue
        ref_ids = v1do.get("referenceIds") or []

        for ref_id in ref_ids:
            # Try each reference kind; a ref id is unique to its kind, so 3 lookups
            # max. 404 is normal (means the ref belongs to a different kind).
            fr = _get_v1_ref(sh, coll_id, do_id, ref_id, "fileReferences")
            if fr:
                file_refs += 1
                fc_id = fr.get("fileContainerId")
                for fid in fr.get("fileIds") or []:
                    finfo = _get_v1_file(sh, fc_id, fid) if fc_id is not None else None
                    if finfo and finfo.get("fileSize") is not None:
                        file_bytes += int(finfo["fileSize"])
                continue
            tr = _get_v1_ref(sh, coll_id, do_id, ref_id, "timeseriesReferences")
            if tr:
                ts_refs += 1
                ts_channels += len(tr.get("timeseries") or [])
                continue
            sr = _get_v1_ref(sh, coll_id, do_id, ref_id, "structuredDataReferences")
            if sr:
                sd_refs += 1
                continue

    tick.set("file_refs_count", file_refs)
    tick.set("file_bytes_total", file_bytes)
    tick.set("ts_refs_count", ts_refs)
    tick.set("ts_channels_total", ts_channels)
    tick.set("sd_refs_count", sd_refs)
    tick.set("garage_bucket_bytes", _garage_bucket_bytes())

    return tick


# ── Write to Shepard ────────────────────────────────────────────────────────────
def write_tick(sh: Shepard, container_id: int, tick: Tick) -> int:
    """POST one TimeseriesWithDataPoints per metric channel. Returns count written."""
    written = 0
    for name in METRIC_CHANNELS:
        body = {
            "timeseries": {
                "measurement": MEASUREMENT,
                "device": DEVICE,
                "location": LOCATION,
                "symbolicName": name,
                "field": FIELD,
            },
            "points": [{"timestamp": tick.timestamp_ns, "value": tick.counters.get(name, 0)}],
        }
        try:
            sh.post(f"/shepard/api/timeseriesContainers/{container_id}/payload", body)
            written += 1
        except requests.HTTPError as exc:
            print(f"[write] channel {name} failed: {exc}", flush=True)
    return written


# ── Bootstrap ───────────────────────────────────────────────────────────────────
def bootstrap(sh: Shepard, coll_app_id: str) -> tuple[int, int]:
    """Create the TS container + DataObject if not present. Returns (container_id, do_id).

    Idempotent: if a container with TS_CONTAINER_NAME already exists, reuses it.
    Same for the DataObject (matches by name within the collection).
    """
    # 1. TS container.
    ts_id: int | None = None
    rows = sh.get("/shepard/api/timeseriesContainers", params={"size": 500}) or []
    for row in rows:
        if row.get("name") == TS_CONTAINER_NAME:
            ts_id = row["id"]
            print(f"[bootstrap] reusing TS container {ts_id} '{TS_CONTAINER_NAME}'", flush=True)
            break
    if ts_id is None:
        created = sh.post("/shepard/api/timeseriesContainers", {"name": TS_CONTAINER_NAME})
        ts_id = created["id"]
        print(f"[bootstrap] created TS container {ts_id} '{TS_CONTAINER_NAME}'", flush=True)

    # 2. Resolve collection numeric id (v2 endpoint accepts appId; v1 requires int).
    coll = sh.get(f"/v2/collections/{coll_app_id}")
    coll_id = coll["id"]

    # 3. DataObject.
    dos = _list_dataobjects(sh, coll_app_id)
    do_id: int | None = None
    for do in dos:
        if do.get("name") == STATS_DO_NAME:
            do_id = do["id"]
            print(f"[bootstrap] reusing DataObject {do_id} '{STATS_DO_NAME}'", flush=True)
            break
    if do_id is None:
        body = {
            "name": STATS_DO_NAME,
            "description": (
                "Self-observability counters for the MFFD-Dropbox import session "
                "2026-05-23-mffd. Every channel records a per-payload-kind count "
                "(DataObjects, file refs+bytes, TS refs+channels, SD refs) plus "
                "Garage bucket size. One tick = every 5 minutes.\n"
                "Generated by scripts/mffd-import-stats-collector.py "
                "(OBS-MFFD1, aidocs/16). Shepard measures itself."
            ),
            "attributes": {
                "session": "2026-05-23-mffd",
                "kind": "self-observability",
                "produced_by": "mffd-import-stats-collector.py",
            },
        }
        created = sh.post(f"/shepard/api/collections/{coll_id}/dataObjects", body)
        do_id = created["id"]
        print(f"[bootstrap] created DataObject {do_id} '{STATS_DO_NAME}'", flush=True)

    print(f"[bootstrap] STATS_TS_CONTAINER_ID={ts_id}", flush=True)
    print(f"[bootstrap] STATS_DO_ID={do_id}", flush=True)
    print(f"[bootstrap] COLL_ID={coll_id}", flush=True)
    print(
        "[bootstrap] note: TimeseriesReference will be auto-created after the "
        "first tick materialises channels (v5 requires non-empty timeseries[] — "
        "Bug A fix from v15).",
        flush=True,
    )
    return ts_id, do_id


def ensure_ts_reference(sh: Shepard, coll_id: int, do_id: int, container_id: int) -> bool:
    """Idempotent: if no TS reference on the DO already points at our container,
    create one listing every channel currently present in the container.

    Returns True if the reference exists at exit; False if it could not be created
    (e.g. container is still empty — caller should retry after the first successful
    payload upload).
    """
    do = _get_v1_do(sh, coll_id, do_id)
    for ref_id in do.get("referenceIds") or []:
        tr = _get_v1_ref(sh, coll_id, do_id, ref_id, "timeseriesReferences")
        if tr and tr.get("timeseriesContainerId") == container_id:
            return True

    channels = sh.get(f"/shepard/api/timeseriesContainers/{container_id}/timeseries") or []
    if not channels:
        print("[ts-ref] container has no channels yet, deferring reference creation", flush=True)
        return False

    series = [
        {
            "measurement": ch["measurement"],
            "device": ch["device"],
            "location": ch["location"],
            "symbolicName": ch["symbolicName"],
            "field": ch["field"],
        }
        for ch in channels
    ]
    body = {
        "name": TS_REF_NAME,
        "timeseriesContainerId": container_id,
        "start": 0,
        "end": (1 << 62),
        "timeseries": series,
    }
    sh.post(
        f"/shepard/api/collections/{coll_id}/dataObjects/{do_id}/timeseriesReferences",
        body,
    )
    print(f"[ts-ref] created reference to container {container_id} with {len(series)} channels", flush=True)
    return True


# ── Main ────────────────────────────────────────────────────────────────────────
def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0] if __doc__ else "")
    ap.add_argument("--bootstrap", action="store_true", help="create container + DO + exit")
    ap.add_argument("--one-tick", action="store_true", help="run one tick + exit (systemd oneshot)")
    args = ap.parse_args()

    api_key = os.environ.get("SHEPARD_API_KEY", "").strip()
    if not api_key:
        print("error: SHEPARD_API_KEY env var is required", file=sys.stderr)
        return 2
    url = os.environ.get("SHEPARD_URL", DEFAULT_URL).rstrip("/")
    coll_app_id = os.environ.get("COLL_APP_ID", DEFAULT_COLL_APP_ID)
    sh = Shepard(base=url, api_key=api_key)

    if args.bootstrap:
        bootstrap(sh, coll_app_id)
        return 0

    ts_id_s = os.environ.get("STATS_TS_CONTAINER_ID", "").strip()
    do_id_s = os.environ.get("STATS_DO_ID", "").strip()
    if not ts_id_s or not do_id_s:
        print(
            "error: STATS_TS_CONTAINER_ID and STATS_DO_ID env vars are required "
            "(run --bootstrap first).",
            file=sys.stderr,
        )
        return 2
    container_id = int(ts_id_s)
    do_id = int(do_id_s)

    # Resolve coll_id for the ensure_ts_reference call (cheap; one GET).
    coll = sh.get(f"/v2/collections/{coll_app_id}")
    coll_id = coll["id"]

    signal.signal(signal.SIGINT, _stop_handler)
    signal.signal(signal.SIGTERM, _stop_handler)

    tick_seconds = int(os.environ.get("TICK_SECONDS", "300"))

    def _one() -> None:
        tick = collect_tick(sh, coll_app_id)
        written = write_tick(sh, container_id, tick)
        print(
            f"[tick] {time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime(tick.timestamp_ns / 1e9))} "
            f"channels_written={written}/{len(METRIC_CHANNELS)} {tick.summary()}",
            flush=True,
        )
        # Lazy-create the TS reference once channels exist.
        try:
            ensure_ts_reference(sh, coll_id, do_id, container_id)
        except requests.HTTPError as exc:
            print(f"[ts-ref] ensure failed (will retry next tick): {exc}", flush=True)

    if args.one_tick:
        _one()
        return 0

    # Loop mode.
    print(f"[loop] starting; tick_seconds={tick_seconds}", flush=True)
    while not _STOP:
        try:
            _one()
        except Exception as exc:  # noqa: BLE001
            print(f"[loop] tick failed: {exc}", flush=True)
        # Sleep in small slices so SIGTERM is responsive.
        slept = 0
        while slept < tick_seconds and not _STOP:
            time.sleep(min(2, tick_seconds - slept))
            slept += 2
    print("[loop] running final tick before exit", flush=True)
    try:
        _one()
    except Exception as exc:  # noqa: BLE001
        print(f"[loop] final tick failed: {exc}", flush=True)
    print("[loop] clean shutdown", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
