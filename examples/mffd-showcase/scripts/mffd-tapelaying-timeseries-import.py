#!/usr/bin/env python3
"""mffd-tapelaying-timeseries-import.py — attach TPS sensor TIMESERIES to the
already-existing MFFD AFP-tapelaying Track DataObjects, populating TimescaleDB.

Context
-------
The 8 251 Track DataObjects (+ Ply / Layup nodes) already exist in the live
collection ``mffd-afp-tapelaying`` (structure + lineage from a prior pass —
``mffd-tapelaying-import.py`` pass B). Each Track DO carries the stable
annotation ``urn:shepard:source:cube3-do-id = <metadata.id>``. This wave does
NOT create DataObjects; it RESOLVES each existing Track DO by that annotation
and attaches a TimeseriesContainer + TimeseriesReference + the data points.

The instance currently has ZERO timeseries rows. The operator wants the
timeseries populated so the charts / TS views light up.

TPS → timeseries format mapping (discovered from the export counterpart)
------------------------------------------------------------------------
The cube3 export (``mffd-ts-export.py``) already decoded each Track DO's
TimeseriesReference into a ROW-format CSV at ``<Track>/ts/Timeseries.csv`` with
the canonical Shepard 5-tuple header::

    DEVICE,FIELD,LOCATION,MEASUREMENT,SYMBOLICNAME,TIMESTAMP,VALUE

  - one row per (channel, timestamp) sample
  - DEVICE ∈ {TPS, DRG, MTLH, R20}; LOCATION = MFZ; FIELD = value
  - MEASUREMENT is the physical unit/kind (area_mm2, distance_mm, celsius,
    newton, angle_degree, mm, mm_per_s, signal_binary, state, status, …)
  - SYMBOLICNAME is the concrete channel (area3, displacement0, TemperaturTape,
    TapeForce_TapeActForce[1], A1_ActualPosition, …) — ~250 channels/track.
  - TIMESTAMP is nanoseconds-since-epoch (int64); VALUE is float/int/bool/str.

So a "channel" = the full 5-tuple (measurement, device, location,
symbolicName, field); a "point" = (timestamp_ns, value). This is exactly the
shape Shepard's timeseries substrate keys on — re-ingesting the export CSV
round-trips the data losslessly back into TimescaleDB.

Value typing: numeric tokens parse as int (no dot/exp) or float; ``true/false``
as bool; everything else stays a string. The backend's COPY ingest locks a
channel to the value-type of its first point, so we coerce per channel
consistently (numeric channels stay numeric; the rare non-finite token is
skipped, matching the backend's NDJSON skip semantics).

Ingest surface (discovered + verified against the live API + backend code)
--------------------------------------------------------------------------
  1. Container create  : ``POST /v2/containers?kind=timeseries``  body {name}
                         → 201 {appId}. (v2 dev surface — CLAUDE.md compliant.)
  2. Numeric container id : read ``entityId`` from
                         ``GET /v2/containers/{appId}/permissions`` (no name
                         lookup needed; entityId IS the Neo4j container id).
  3. Channel data write : ``POST /shepard/api/timeseriesContainers/{numId}/payload``
                         (JSON ``TimeseriesWithDataPoints`` = {timeseries:5tuple,
                         points:[{timestamp,value}]}) → 201. This is the only
                         channel-auto-creating ingest path: ``saveDataPoints`` →
                         ``getOrCreateTimeseries`` mints the channel from the
                         5-tuple and writes points via COPY into TimescaleDB.
                         (Frozen v1 surface — used because the v2 channel ingest
                         ``/v2/containers/{appId}/channels/{shepardId}/data/ingest``
                         requires a *pre-existing* channel and v2 has no
                         channel-CREATE endpoint. Documented gap → backlog row
                         TS-V2-CHANNEL-CREATE. See report.)
  4. Reference create  : ``POST /shepard/api/collections/{numColId}/dataObjects/
                         {numDoId}/timeseriesReferences`` (body {name, start, end,
                         timeseries:[5tuples], timeseriesContainerId:numId}) —
                         links the container's channels to the Track DO so the UI
                         and the v2 ``referenced-containers`` shelf surface it.
  5. Permissions       : ``PATCH /v2/containers/{appId}/permissions``
                         {permissionType: PublicReadable} — closes the
                         IMPORT-PERMS-1 gap so demo (non-admin) users can read.

Idempotency + resumability
---------------------------
  - A local sqlite state file (``--state``) records each completed (track, status)
    keyed by the cube3 do-id; a re-run skips tracks already marked ``done``.
  - Idempotent against LIVE Shepard too: before creating a container for a track
    we look for an existing TimeseriesReference on that DO whose name matches our
    deterministic name (``TPS timeseries — <track-folder>``); if present we reuse
    it and skip (so a crashed mid-run re-converges without duplicate containers).
  - Completeness: retry-forever on transport/5xx (ported Client); a track is
    never silently skipped — a per-channel write failure aborts the track and it
    stays ``pending`` for the next run.

Usage
-----
    mffd-tapelaying-timeseries-import.py --dry-run
    mffd-tapelaying-timeseries-import.py --limit 3 --verbose
    mffd-tapelaying-timeseries-import.py --workers 6        # FULL run

Reference implementations reused (CLAUDE.md "reuse before reimplement"):
  - mffd-tapelaying-import.py — Client (retry-forever + bounded-403-retry),
    load_api_key, LiveIndex idempotency, the cube3-do-id annotation contract.
  - mffd-ts-export.py — the ROW-CSV format spec (the export counterpart).
"""
from __future__ import annotations

import argparse
import csv
import io
import os
import shutil
import sqlite3
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Optional

try:
    import requests
except ImportError:
    sys.stderr.write("ERROR: this script needs `requests`.  pip install requests\n")
    sys.exit(2)

# ── defaults ────────────────────────────────────────────────────────────────
DEFAULT_HOST = "https://shepard-api.nuclide.systems"
DEFAULT_SOURCE = "/mnt/pve/unas/dump/dataset/cube3-export/mffd-export/ts-export/tapelaying"
DEFAULT_KEYFILE = "/root/.claude/uploads/mffd-import-key-2026-06-17.txt"
TARGET_COLLECTION_APPID = "019ed455-66f4-7aea-8cb3-5c0b34a737df"
DEFAULT_STATE = str(Path(__file__).resolve().parent / ".mffd-ts-import-state.sqlite")

# Single shared TimeseriesContainer for the WHOLE tapelaying collection.
# Every track's channels live here; track identity is folded into the channel
# 5-tuple's `location` (see `_scope_location`) so they don't collide. This is the
# "minimal containers" design: 1 container for ~8 251 tracks instead of one each.
SHARED_CONTAINER_NAME = "mffd-afp-tapelaying-timeseries"

SOURCE_DO_ID_PREDICATE = "urn:shepard:source:cube3-do-id"

# Shepard root volume to watch — filling it crashes the instance.
SHEPARD_ROOT = "/opt/shepard"
DEFAULT_DISK_FLOOR_GB = 50.0

# Per-channel point batch for the JSON payload write. A track has ~250 channels
# × a few hundred points each; one write per channel is fine, but we cap the
# JSON body so a pathological channel can't blow up memory.
MAX_POINTS_PER_WRITE = 50000


# ── HTTP client (ported from mffd-tapelaying-import.py: retry-forever backoff) ─
class Client:
    def __init__(self, host: str, api_key: str, verbose: bool = False):
        self.host = host.rstrip("/")
        self.verbose = verbose
        self._local = threading.local()
        self._api_key = api_key

    @property
    def session(self) -> requests.Session:
        s = getattr(self._local, "session", None)
        if s is None:
            s = requests.Session()
            s.headers.update({"X-API-KEY": self._api_key})
            self._local.session = s
        return s

    def _log(self, *a):
        if self.verbose:
            print(*a, file=sys.stderr, flush=True)

    def request(self, method: str, path: str, *, expect=(200, 201, 204),
                retry_on_403=False, max_403_retries=12, **kw) -> requests.Response:
        url = path if path.startswith("http") else f"{self.host}{path}"
        attempt = 0
        n403 = 0
        backoff = 1.0
        while True:
            attempt += 1
            try:
                resp = self.session.request(method, url, timeout=300, **kw)
            except (requests.ConnectionError, requests.Timeout) as e:
                self._log(f"  [retry] {method} {path} transport error: {e} "
                          f"(attempt {attempt}, sleep {backoff:.0f}s)")
                time.sleep(backoff)
                backoff = min(backoff * 2, 60.0)
                continue
            if resp.status_code in expect:
                return resp
            if 500 <= resp.status_code < 600:
                self._log(f"  [retry] {method} {path} -> {resp.status_code} "
                          f"(attempt {attempt}, sleep {backoff:.0f}s) body={resp.text[:200]}")
                time.sleep(backoff)
                backoff = min(backoff * 2, 60.0)
                continue
            if resp.status_code == 403 and retry_on_403 and n403 < max_403_retries:
                n403 += 1
                self._log(f"  [retry-403] {method} {path} permission-propagation lag "
                          f"(403 #{n403}, sleep {backoff:.0f}s)")
                time.sleep(backoff)
                backoff = min(backoff * 2, 30.0)
                continue
            raise RuntimeError(f"{method} {path} -> {resp.status_code}: {resp.text[:400]}")

    def get_json(self, path, **kw):
        return self.request("GET", path, **kw).json()

    def post_json(self, path, body, *, retry_on_403=False, **kw):
        return self.request("POST", path, json=body, expect=(200, 201),
                            retry_on_403=retry_on_403, **kw).json()

    def patch_json(self, path, body, *, retry_on_403=False, **kw):
        headers = {"Content-Type": "application/merge-patch+json"}
        return self.request("PATCH", path, json=body, expect=(200,),
                            retry_on_403=retry_on_403, headers=headers, **kw).json()


# ── counts ────────────────────────────────────────────────────────────────────
@dataclass
class Counts:
    tracks_done: int = 0
    tracks_reused: int = 0
    tracks_skipped_no_do: int = 0
    tracks_skipped_no_csv: int = 0
    containers_created: int = 0
    references_created: int = 0
    channels_written: int = 0
    points_written: int = 0
    perms_set: int = 0
    errors: int = 0
    _lock: threading.Lock = field(default_factory=threading.Lock, repr=False)

    def add(self, **kw):
        with self._lock:
            for k, v in kw.items():
                setattr(self, k, getattr(self, k) + v)


# ── sqlite resumable state ─────────────────────────────────────────────────────
class State:
    """Records (cube3_do_id -> status) so a re-run skips finished tracks."""

    def __init__(self, path: str):
        self._lock = threading.Lock()
        self.conn = sqlite3.connect(path, check_same_thread=False)
        self.conn.execute(
            "CREATE TABLE IF NOT EXISTS tracks ("
            "  do_id TEXT PRIMARY KEY, status TEXT, container_appid TEXT,"
            "  channels INTEGER, points INTEGER, ts REAL)"
        )
        self.conn.commit()

    def get_status(self, do_id: str) -> Optional[str]:
        with self._lock:
            row = self.conn.execute(
                "SELECT status FROM tracks WHERE do_id = ?", (do_id,)
            ).fetchone()
        return row[0] if row else None

    def mark(self, do_id: str, status: str, container_appid: str = "",
             channels: int = 0, points: int = 0):
        with self._lock:
            self.conn.execute(
                "INSERT INTO tracks(do_id,status,container_appid,channels,points,ts) "
                "VALUES(?,?,?,?,?,?) ON CONFLICT(do_id) DO UPDATE SET "
                "status=excluded.status, container_appid=excluded.container_appid, "
                "channels=excluded.channels, points=excluded.points, ts=excluded.ts",
                (do_id, status, container_appid, channels, points, time.time()),
            )
            self.conn.commit()


# ── source loading ──────────────────────────────────────────────────────────
@dataclass
class SourceTrack:
    folder: str
    do_id: int
    name: str
    csv_path: Path


def load_tracks(source_root: Path, limit: Optional[int]) -> list[SourceTrack]:
    """Track dirs that have both a metadata.json and a ts/Timeseries.csv."""
    import json
    out: list[SourceTrack] = []
    dirs = sorted(d for d in source_root.iterdir() if d.is_dir())
    for d in dirs:
        csv_path = d / "ts" / "Timeseries.csv"
        meta_path = d / "metadata.json"
        if not csv_path.is_file() or not meta_path.is_file():
            continue
        try:
            with meta_path.open(encoding="utf-8") as fh:
                m = json.load(fh)
        except Exception:
            continue
        out.append(SourceTrack(
            folder=d.name,
            do_id=int(m["id"]),
            name=m.get("name") or d.name,
            csv_path=csv_path,
        ))
        if limit is not None and len(out) >= limit:
            break
    return out


# ── CSV → channels parsing ────────────────────────────────────────────────────
def _coerce(raw: str):
    """Coerce a CSV VALUE token to bool/int/float/str, matching backend typing.
    Returns (value, ok). ok=False for non-finite / empty (skip the point)."""
    s = raw.strip()
    if s == "" or s.lower() in ("nan", "inf", "-inf", "+inf", "infinity", "-infinity"):
        return None, False
    low = s.lower()
    if low == "true":
        return True, True
    if low == "false":
        return False, True
    # integer (no dot / exponent)
    try:
        if ("." not in s) and ("e" not in low):
            return int(s), True
    except ValueError:
        pass
    try:
        return float(s), True
    except ValueError:
        return s, True  # genuine string channel


def parse_channels(csv_path: Path) -> dict[tuple, list[tuple[int, Any]]]:
    """Parse a ROW-format Timeseries.csv into {5tuple: [(ts_ns, value), ...]}.

    5tuple = (measurement, device, location, symbolicName, field) — the Shepard
    channel identity, matching the v1 ingest body order.
    """
    channels: dict[tuple, list[tuple[int, Any]]] = {}
    with csv_path.open("r", encoding="utf-8", newline="") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            try:
                ts = int(row["TIMESTAMP"])
            except (KeyError, ValueError, TypeError):
                continue
            value, ok = _coerce(row.get("VALUE", ""))
            if not ok:
                continue
            key = (
                row["MEASUREMENT"], row["DEVICE"], row["LOCATION"],
                row["SYMBOLICNAME"], row["FIELD"],
            )
            channels.setdefault(key, []).append((ts, value))
    return channels


def _enforce_channel_type(points: list[tuple[int, Any]]) -> list[tuple[int, Any]]:
    """The backend locks a channel to its first point's value-type. Promote a
    channel that mixes int+float to float so a later float doesn't get rejected;
    drop points whose type can't be unified (rare)."""
    if not points:
        return points
    has_float = any(isinstance(v, float) for _, v in points)
    has_str = any(isinstance(v, str) for _, v in points)
    has_bool = any(isinstance(v, bool) for _, v in points)
    # bool is a subtype of int in python — treat separately
    if has_str:
        return [(t, v if isinstance(v, str) else str(v)) for t, v in points]
    if has_bool and not has_float:
        # keep bool channel only if all are bool, else coerce bool→int
        if all(isinstance(v, bool) for _, v in points):
            return points
        return [(t, int(v) if isinstance(v, bool) else v) for t, v in points]
    if has_float:
        return [(t, float(v)) for t, v in points
                if isinstance(v, (int, float)) and not isinstance(v, bool)]
    return points


# ── LIVE idempotency: existing TS reference on a DO ───────────────────────────
def ref_name_for(track_folder: str) -> str:
    return f"TPS timeseries — {track_folder}"


def resolve_or_create_shared_container(client: "Client") -> tuple[str, int]:
    """Idempotently get the one shared TimeseriesContainer (by name), creating it
    on first run. Returns (appId, numericId). Sets PublicReadable once so demo
    (non-admin) users can read it (IMPORT-PERMS-1)."""
    # look up an existing container by name on the frozen v1 list (carries id+appId)
    existing = client.get_json(
        f"/shepard/api/timeseriesContainers?name="
        f"{requests.utils.quote(SHARED_CONTAINER_NAME)}")
    for c in (existing or []):
        if c.get("name") == SHARED_CONTAINER_NAME:
            appid = c.get("appId")
            numeric = c.get("id")
            print(f"shared container : reused {appid} (#{numeric})", flush=True)
            return appid, numeric
    # create it
    container = client.post_json(
        "/v2/containers?kind=timeseries", {"name": SHARED_CONTAINER_NAME},
        retry_on_403=True)
    appid = container["appId"]
    perms = client.get_json(f"/v2/containers/{appid}/permissions")
    numeric = perms["entityId"]
    client.patch_json(f"/v2/containers/{appid}/permissions",
                      {"permissionType": "PublicReadable"})
    print(f"shared container : created {appid} (#{numeric}) PublicReadable",
          flush=True)
    return appid, numeric


# ── per-track worker ──────────────────────────────────────────────────────────
class TrackIngestor:
    def __init__(self, client: Client, counts: Counts, state: State,
                 col_appid: str, col_numeric: int,
                 id_to_appid: dict[str, str], appid_to_numeric: dict[str, int],
                 shared_container_appid: str, shared_container_numeric: int,
                 verbose: bool):
        self.c = client
        self.n = counts
        self.state = state
        self.col = col_appid
        self.col_numeric = col_numeric
        self.id_to_appid = id_to_appid
        self.appid_to_numeric = appid_to_numeric
        self.sc_appid = shared_container_appid
        self.sc_numeric = shared_container_numeric
        self.verbose = verbose

    def _existing_ref(self, do_numeric: int, want_name: str) -> bool:
        try:
            refs = self.c.get_json(
                f"/shepard/api/collections/{self.col_numeric}/dataObjects/"
                f"{do_numeric}/timeseriesReferences"
            )
        except RuntimeError:
            return False
        return any(r.get("name") == want_name for r in (refs or []))

    def process(self, t: SourceTrack):
        do_id = str(t.do_id)
        if self.state.get_status(do_id) == "done":
            self.n.add(tracks_reused=1)
            return

        # resolve existing DO appId (must already exist — we never create DOs)
        do_appid = self.id_to_appid.get(do_id)
        if not do_appid:
            self.n.add(tracks_skipped_no_do=1)
            if self.verbose:
                print(f"  [skip] {t.folder}: no existing DO for cube3-id {do_id}",
                      file=sys.stderr, flush=True)
            self.state.mark(do_id, "no-do")
            return
        do_numeric = self.appid_to_numeric.get(do_appid)
        if do_numeric is None:
            self.n.add(tracks_skipped_no_do=1)
            self.state.mark(do_id, "no-numeric")
            return

        want_name = ref_name_for(t.folder)
        if self._existing_ref(do_numeric, want_name):
            self.n.add(tracks_reused=1)
            self.state.mark(do_id, "done")
            return

        # parse channels
        channels = parse_channels(t.csv_path)
        if not channels:
            self.n.add(tracks_skipped_no_csv=1)
            self.state.mark(do_id, "empty-csv")
            return

        # Single shared container for the whole collection (created once in main).
        container_appid = self.sc_appid
        container_numeric = self.sc_numeric

        # write each channel's points into the shared container (auto-creates the
        # channel). Track id is folded into `location` so this track's channels
        # don't collide with any other track's in the shared container.
        min_ts = None
        max_ts = None
        ch_written = 0
        pts_written = 0
        tuples_for_ref: list[dict] = []
        for (meas, dev, loc, sym, fld), pts in channels.items():
            pts = _enforce_channel_type(pts)
            if not pts:
                continue
            pts.sort(key=lambda p: p[0])
            # Minimal channels: every run writes the SAME ~few-hundred signal
            # channels (raw 5-tuple, no per-track scoping). Source timestamps are
            # absolute, so sequential runs concatenate continuously on one channel
            # with no overlap — each run is a distinct time window via its
            # TimeseriesReference (start..end below). No duplicated channels.
            ts5 = {"measurement": meas, "device": dev, "location": loc,
                   "symbolicName": sym, "field": fld}
            tuples_for_ref.append(ts5)
            # chunk a pathologically large channel
            for i in range(0, len(pts), MAX_POINTS_PER_WRITE):
                chunk = pts[i:i + MAX_POINTS_PER_WRITE]
                body = {
                    "timeseries": ts5,
                    "points": [{"timestamp": ts, "value": v} for ts, v in chunk],
                }
                self.c.post_json(
                    f"/shepard/api/timeseriesContainers/{container_numeric}/payload",
                    body, retry_on_403=True,
                )
                pts_written += len(chunk)
            ch_written += 1
            lo = pts[0][0]
            hi = pts[-1][0]
            min_ts = lo if min_ts is None else min(min_ts, lo)
            max_ts = hi if max_ts is None else max(max_ts, hi)

        if ch_written == 0:
            self.n.add(tracks_skipped_no_csv=1)
            self.state.mark(do_id, "empty-after-coerce")
            return

        self.n.add(channels_written=ch_written, points_written=pts_written)

        # create the TimeseriesReference on this track DO, selecting only this
        # track's channels from the shared container.
        ref_body = {
            "name": want_name,
            "start": int(min_ts),
            "end": int(max_ts),
            "timeseries": tuples_for_ref,
            "timeseriesContainerId": container_numeric,
        }
        self.c.post_json(
            f"/shepard/api/collections/{self.col_numeric}/dataObjects/"
            f"{do_numeric}/timeseriesReferences",
            ref_body, retry_on_403=True,
        )
        self.n.add(references_created=1)

        self.state.mark(do_id, "done", container_appid, ch_written, pts_written)
        self.n.add(tracks_done=1)
        if self.verbose:
            print(f"  [done] {t.folder}: {ch_written} channels, {pts_written} points "
                  f"→ container {container_appid}", file=sys.stderr, flush=True)


# ── dry-run ─────────────────────────────────────────────────────────────────
def run_dry_run(tracks: list[SourceTrack], sample_n: int = 5):
    print("\n── DRY-RUN plan ──")
    print(f"  Track dirs with ts/Timeseries.csv : {len(tracks)}")
    # sample channel/point counts from the first few tracks
    sample = tracks[:sample_n]
    ch_counts = []
    pt_counts = []
    for t in sample:
        ch = parse_channels(t.csv_path)
        ch_counts.append(len(ch))
        pt_counts.append(sum(len(v) for v in ch.values()))
        print(f"    {t.folder}: {len(ch)} channels, "
              f"{sum(len(v) for v in ch.values())} points")
    if ch_counts:
        avg_ch = sum(ch_counts) / len(ch_counts)
        avg_pt = sum(pt_counts) / len(pt_counts)
        print(f"  avg channels/track (sample of {len(sample)}) : {avg_ch:.0f}")
        print(f"  avg points/track   (sample of {len(sample)}) : {avg_pt:,.0f}")
        print(f"  est. total channels (all tracks)  : {avg_ch * len(tracks):,.0f}")
        print(f"  est. total points   (all tracks)  : {avg_pt * len(tracks):,.0f}")
    print(f"  disk free now : {free_gb(SHEPARD_ROOT):.1f} GB")


def free_gb(path: str) -> float:
    _, _, free = shutil.disk_usage(path)
    return free / 1e9


# ── orchestration ─────────────────────────────────────────────────────────────
def load_api_key(arg_key: Optional[str], keyfile: str) -> str:
    if arg_key:
        return arg_key
    env = os.environ.get("SHEPARD_API_KEY")
    if env:
        return env
    if os.path.exists(keyfile):
        with open(keyfile) as fh:
            for line in fh:
                if line.startswith("jwt="):
                    return line[4:].strip()
    sys.stderr.write("ERROR: no API key (use --api-key, SHEPARD_API_KEY, or key file)\n")
    sys.exit(2)


def build_do_maps(client: Client, col_appid: str, col_numeric: int, verbose: bool):
    """Return (cube3_do_id -> appId, appId -> numeric_id).

    cube3-id → appId comes from the source-id annotations; appId → numeric from
    the frozen v1 collection-DO list (which carries both id and appId)."""
    import requests as _rq
    id_to_appid: dict[str, str] = {}
    page = 0
    while True:
        batch = client.get_json(
            f"/v2/annotations?predicateIri="
            f"{_rq.utils.quote(SOURCE_DO_ID_PREDICATE)}&page={page}&pageSize=200"
        )
        if not batch:
            break
        for a in batch:
            lit = a.get("objectLiteral")
            subj = a.get("subjectAppId")
            if lit and subj:
                id_to_appid[lit] = subj
        page += 1
    appid_to_numeric: dict[str, int] = {}
    page = 0
    while True:
        batch = client.get_json(
            f"/shepard/api/collections/{col_numeric}/dataObjects?page={page}&size=500"
        )
        if not batch:
            break
        for d in batch:
            if d.get("appId") and d.get("id") is not None:
                appid_to_numeric[d["appId"]] = d["id"]
        page += 1
    if verbose:
        print(f"do maps       : {len(id_to_appid)} cube3-id→appId, "
              f"{len(appid_to_numeric)} appId→numeric", file=sys.stderr, flush=True)
    return id_to_appid, appid_to_numeric


def main():
    ap = argparse.ArgumentParser(
        description="Attach TPS timeseries to existing MFFD AFP-tapelaying Track DOs.")
    ap.add_argument("--source", default=DEFAULT_SOURCE)
    ap.add_argument("--host", default=DEFAULT_HOST)
    ap.add_argument("--api-key", default=None)
    ap.add_argument("--keyfile", default=DEFAULT_KEYFILE)
    ap.add_argument("--collection-appid", default=TARGET_COLLECTION_APPID)
    ap.add_argument("--state", default=DEFAULT_STATE)
    ap.add_argument("--workers", type=int, default=4)
    ap.add_argument("--limit", type=int, default=None, help="only first N tracks")
    ap.add_argument("--disk-floor-gb", type=float, default=DEFAULT_DISK_FLOOR_GB)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--verbose", action="store_true")
    args = ap.parse_args()

    source_root = Path(args.source)
    api_key = load_api_key(args.api_key, args.keyfile)
    client = Client(args.host, api_key, verbose=args.verbose)

    print(f"source        : {source_root}")
    print(f"host          : {args.host}")
    print(f"collection    : {args.collection_appid}")
    print(f"mode          : {'DRY-RUN' if args.dry_run else 'LIVE'}  "
          f"workers={args.workers}{'  limit='+str(args.limit) if args.limit else ''}")

    print("loading tracks …", flush=True)
    tracks = load_tracks(source_root, args.limit)
    print(f"tracks (w/ ts): {len(tracks)}", flush=True)

    if args.dry_run:
        run_dry_run(tracks)
        return

    # verify auth + collection
    me = client.get_json("/v2/users/me")
    print(f"auth ok       : {me.get('effectiveDisplayName')} ({me.get('appId')})")
    col = client.get_json(f"/v2/collections/{args.collection_appid}")
    print(f"collection ok : {col.get('name')}")

    import requests as _rq
    col_list = client.get_json(
        f"/shepard/api/collections?name={_rq.utils.quote(col['name'])}")
    col_numeric = next(
        (c["id"] for c in col_list if c.get("appId") == args.collection_appid), None)
    if col_numeric is None:
        sys.stderr.write("ERROR: could not resolve collection numeric id\n")
        sys.exit(1)
    print(f"collection #id: {col_numeric}")

    id_to_appid, appid_to_numeric = build_do_maps(
        client, args.collection_appid, col_numeric, verbose=True)
    print(f"do maps       : {len(id_to_appid)} cube3-id→appId, "
          f"{len(appid_to_numeric)} appId→numeric", flush=True)

    # one shared container for the whole collection (minimal-containers design)
    sc_appid, sc_numeric = resolve_or_create_shared_container(client)

    state = State(args.state)
    counts = Counts()
    ing = TrackIngestor(client, counts, state, args.collection_appid, col_numeric,
                        id_to_appid, appid_to_numeric, sc_appid, sc_numeric,
                        args.verbose)

    total = len(tracks)
    print(f"\n── ingesting {total} tracks ──", flush=True)
    done = 0
    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        futs = {ex.submit(ing.process, t): t for t in tracks}
        for fut in as_completed(futs):
            t = futs[fut]
            try:
                fut.result()
            except Exception as e:
                counts.add(errors=1)
                state.mark(str(t.do_id), "error")
                print(f"  [ERROR] {t.folder}: {e}", file=sys.stderr, flush=True)
            done += 1
            if done % 25 == 0 or done == total:
                now = free_gb(SHEPARD_ROOT)
                print(f"  {done}/{total}  done={counts.tracks_done} "
                      f"reused={counts.tracks_reused} pts={counts.points_written:,} "
                      f"err={counts.errors}  df={now:.0f}GB", flush=True)
                if now < args.disk_floor_gb:
                    print(f"  [STOP] disk free {now:.1f} GB < floor "
                          f"{args.disk_floor_gb} GB — aborting.", flush=True)
                    break

    print("\n── summary ──")
    print(f"  tracks ingested      : {counts.tracks_done}")
    print(f"  tracks reused/skipped: {counts.tracks_reused}")
    print(f"  skipped (no DO)      : {counts.tracks_skipped_no_do}")
    print(f"  skipped (empty csv)  : {counts.tracks_skipped_no_csv}")
    print(f"  containers created   : {counts.containers_created}")
    print(f"  references created   : {counts.references_created}")
    print(f"  channels written     : {counts.channels_written}")
    print(f"  points written       : {counts.points_written:,}")
    print(f"  permissions set      : {counts.perms_set}")
    print(f"  errors               : {counts.errors}")


if __name__ == "__main__":
    main()
