"""TS-IDc demonstration seed — AFP tool-centre-point (TCP) kinematics + orientation.

Creates a single Collection, DataObject, and TimeseriesContainer with seven
channels representing a simulated AFP consolidation pass:
  tcp_x / tcp_y / tcp_z  — tool-centre-point position (metres)
  head_temp              — pyrometer thermal profile (celsius)
  rot_a / rot_b / rot_c  — KUKA Euler ZYX orientation (degrees)
                           A = yaw (world Z), B = pitch (world Y), C = roll (world X)

The rot_a/b/c channels bind to the Trace3D renderer's orientation roles:
open the ViewRecipeBuilder in the UI → assign channels → Open Trace3D.

The data is visible in the normal Shepard UI under
Collections → "TS-IDc demo — AFP TCP thermal trail".

The --demo flag exercises the TS-IDc endpoint after seeding:
  1. GET /v2/timeseries-containers/{id}/channels  → list channels + shepardIds
  2. GET /v2/timeseries-containers/{id}/channels/{uuid}/data  → fetch by shepardId

Usage
-----
    python seed.py --host https://shepard.nuclide.systems/shepard/api \\
                   --apikey YOUR_KEY

    python seed.py --host https://shepard.nuclide.systems/shepard/api \\
                   --apikey YOUR_KEY --demo

    python seed.py ... --reset     (delete & re-create)
"""
from __future__ import annotations

import argparse
import json
import math
import sys
import time
import urllib.request
import urllib.error
from datetime import datetime, timedelta, timezone
from typing import Any

# ── seed constants ─────────────────────────────────────────────────────────────

COLLECTION_NAME = "TS-IDc demo — AFP TCP thermal trail"
DO_NAME         = "MFFD-DEMO-AFP-RUN-001 — TS-IDc demonstration"
TSC_NAME        = "AFP-TCP-thermal-trail"

SAMPLE_RATE_HZ  = 25
DURATION_S      = 40
N_SAMPLES       = SAMPLE_RATE_HZ * DURATION_S   # 1000

# AFP tool path: a simple helical trajectory over the mould surface.
# tcp_x / tcp_y = circular sweep (radius ~0.6 m), tcp_z = slow vertical advance
# head_temp = thermal profile: ramp up → consolidation plateau → cool-down
CHANNELS: list[dict] = [
    {
        "measurement": "kinematics",
        "device":       "afp-robot",
        "location":     "mould-1",
        "symbolicName": "tcp_x",
        "field":        "metres",
        "valueType":    "Double",
    },
    {
        "measurement": "kinematics",
        "device":       "afp-robot",
        "location":     "mould-1",
        "symbolicName": "tcp_y",
        "field":        "metres",
        "valueType":    "Double",
    },
    {
        "measurement": "kinematics",
        "device":       "afp-robot",
        "location":     "mould-1",
        "symbolicName": "tcp_z",
        "field":        "metres",
        "valueType":    "Double",
    },
    {
        "measurement": "thermal",
        "device":       "pyrometer-head",
        "location":     "nip-point",
        "symbolicName": "head_temp",
        "field":        "celsius",
        "valueType":    "Double",
    },
    # KUKA Euler orientation angles (extrinsic ZYX convention):
    #   rot_a = rotation around world Z (yaw)   — sweeps -180→+180 across the layup pass
    #   rot_b = rotation around world Y (pitch) — small ±5° approach-angle oscillation
    #   rot_c = rotation around world X (roll)  — small ±3° cross-path oscillation
    # symbolicName matches the Trace3D renderer's rot_a/rot_b/rot_c role bindings.
    {
        "measurement": "kinematics",
        "device":       "afp-robot",
        "location":     "mould-1",
        "symbolicName": "rot_a",
        "field":        "degrees",
        "valueType":    "Double",
    },
    {
        "measurement": "kinematics",
        "device":       "afp-robot",
        "location":     "mould-1",
        "symbolicName": "rot_b",
        "field":        "degrees",
        "valueType":    "Double",
    },
    {
        "measurement": "kinematics",
        "device":       "afp-robot",
        "location":     "mould-1",
        "symbolicName": "rot_c",
        "field":        "degrees",
        "valueType":    "Double",
    },
]


def _generate_points(channel_key: str, n: int, t0_ns: int) -> list[tuple[int, float]]:
    """Return (timestamp_ns, value) pairs for the given channel."""
    dt_ns = int(1e9 / SAMPLE_RATE_HZ)
    pts: list[tuple[int, float]] = []
    for i in range(n):
        t = i / n
        ts = t0_ns + i * dt_ns
        if channel_key == "tcp_x":
            v = 0.6 * math.cos(2 * math.pi * t * 3)
        elif channel_key == "tcp_y":
            v = 0.6 * math.sin(2 * math.pi * t * 3)
        elif channel_key == "tcp_z":
            v = 0.02 + t * 0.15      # 0 → 150 mm linear advance
        elif channel_key == "rot_a":
            # KUKA A: yaw around world Z — linear sweep -180° → +180° across pass.
            # Simplified (task spec); physical tangent-derived yaw would be
            # atan2(d(tcp_y)/dt, d(tcp_x)/dt) — equivalent at 1 rev/pass.
            v = -180.0 + 360.0 * t
        elif channel_key == "rot_b":
            # KUKA B: pitch around world Y — ±5° layup approach-angle oscillation
            v = 5.0 * math.sin(2 * math.pi * t * 7)
        elif channel_key == "rot_c":
            # KUKA C: roll around world X — ±3° cross-path oscillation
            v = 3.0 * math.sin(2 * math.pi * t * 11)
        else:                         # head_temp — ramp / plateau / cool
            if t < 0.15:
                v = 20 + (320 - 20) * (t / 0.15)
            elif t < 0.80:
                v = 320 + 8 * math.sin(2 * math.pi * t * 12)
            else:
                v = 320 - (320 - 80) * ((t - 0.80) / 0.20)
        pts.append((ts, round(v, 4)))
    return pts


# ── HTTP helpers ───────────────────────────────────────────────────────────────

def _req(
    method: str,
    url: str,
    headers: dict,
    body: Any = None,
    *,
    ok_statuses: tuple[int, ...] = (200, 201),
) -> tuple[int, Any]:
    data = json.dumps(body).encode() if body is not None else None
    h = {**headers, "Content-Type": "application/json", "Accept": "application/json"}
    req = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            text = resp.read().decode()
            return resp.status, json.loads(text) if text else {}
    except urllib.error.HTTPError as e:
        text = e.read().decode()
        if e.code in ok_statuses:
            return e.code, json.loads(text) if text else {}
        raise RuntimeError(f"HTTP {e.code} {method} {url}: {text[:300]}") from e


def _v1(host: str) -> str:
    return host.rstrip("/")

def _v2(host: str) -> str:
    return host.rstrip("/").replace("/shepard/api", "")


# ── seeding helpers ────────────────────────────────────────────────────────────

def _find_collection(v1: str, headers: dict, name: str) -> int | None:
    _, body = _req("GET", f"{v1}/collections", headers)
    for c in body if isinstance(body, list) else (body.get("content") or []):
        if c.get("name") == name:
            return c["id"]
    return None


def _ensure_collection(v1: str, headers: dict) -> int:
    cid = _find_collection(v1, headers, COLLECTION_NAME)
    if cid:
        print(f"  SKIP  collection '{COLLECTION_NAME}' id={cid}")
        return cid
    _, body = _req("POST", f"{v1}/collections", headers, {
        "name":        COLLECTION_NAME,
        "description": (
            "Demonstration dataset for TS-IDc (TS-ID PR-3) and Trace3D orientation rendering. "
            "Contains one DataObject with a TimeseriesContainer holding seven AFP "
            "tool-centre-point channels: tcp_x/y/z (position, metres), head_temp "
            "(pyrometer, celsius), and rot_a/rot_b/rot_c (KUKA Euler ZYX orientation, "
            "degrees). The rot_a/b/c channels bind directly to the Trace3D renderer's "
            "orientation roles. Each channel carries a stable `shepardId` (UUID): "
            "GET /v2/timeseries-containers/{id}/channels/{shepardId}/data"
        ),
    })
    cid = body["id"]
    print(f"  OK    collection '{COLLECTION_NAME}' id={cid}")
    return cid


def _ensure_dataobject(v1: str, headers: dict, cid: int) -> int:
    _, dos = _req("GET", f"{v1}/collections/{cid}/dataObjects", headers)
    for do in dos if isinstance(dos, list) else (dos.get("content") or []):
        if do.get("name") == DO_NAME:
            print(f"  SKIP  dataobject '{DO_NAME}' id={do['id']}")
            return do["id"]
    _, body = _req("POST", f"{v1}/collections/{cid}/dataObjects", headers, {
        "name":        DO_NAME,
        "description": (
            "AFP consolidation pass — synthetic TCP trajectory + pyrometer thermal profile "
            "+ KUKA Euler A/B/C orientation (extrinsic ZYX: A=yaw/worldZ, B=pitch/worldY, "
            "C=roll/worldX). Channels rot_a/b/c bind to the Trace3D orientation roles."
        ),
    })
    print(f"  OK    dataobject '{DO_NAME}' id={body['id']}")
    return body["id"]


def _ensure_tsc(v1: str, headers: dict) -> int:
    _, body = _req("GET", f"{v1}/timeseriesContainers", headers)
    for c in body if isinstance(body, list) else (body.get("content") or []):
        if c.get("name") == TSC_NAME:
            print(f"  SKIP  TimeseriesContainer '{TSC_NAME}' id={c['id']}")
            return c["id"]
    _, body = _req("POST", f"{v1}/timeseriesContainers", headers, {"name": TSC_NAME})
    print(f"  OK    TimeseriesContainer '{TSC_NAME}' id={body['id']}")
    return body["id"]


def _ensure_ts_reference(v1: str, headers: dict, cid: int, do_id: int, tsc_id: int, t0_ns: int, t_end_ns: int) -> None:
    _, refs = _req("GET", f"{v1}/collections/{cid}/dataObjects/{do_id}/timeseriesReferences", headers)
    for r in refs if isinstance(refs, list) else []:
        if r.get("timeseriesContainerId") == tsc_id:
            print(f"  SKIP  timeseriesReference → tsc {tsc_id}")
            return
    ts_list = [
        {
            "measurement":  ch["measurement"],
            "device":       ch["device"],
            "location":     ch["location"],
            "symbolicName": ch["symbolicName"],
            "field":        ch["field"],
        }
        for ch in CHANNELS
    ]
    _req("POST", f"{v1}/collections/{cid}/dataObjects/{do_id}/timeseriesReferences", headers, {
        "name":                  TSC_NAME,
        "start":                 t0_ns,
        "end":                   t_end_ns,
        "timeseries":            ts_list,
        "timeseriesContainerId": tsc_id,
    })
    print(f"  OK    timeseriesReference coll={cid} do={do_id} → tsc={tsc_id}")


def _seed_channel(v1: str, headers: dict, tsc_id: int, ch: dict, t0_ns: int) -> None:
    """Upload one channel's data into tsc_id. Idempotent via try-create."""
    sym = ch["symbolicName"]
    pts = _generate_points(sym, N_SAMPLES, t0_ns)

    # Insert first batch (creates the timeseries row if it doesn't exist).
    # v1 upload endpoint is /{containerId}/payload (not /timeseries).
    chunk = [{"timestamp": ts, "value": v} for ts, v in pts[:200]]
    url = f"{v1}/timeseriesContainers/{tsc_id}/payload"
    try:
        _req("POST", url, headers, {
            "timeseries": {
                "measurement":  ch["measurement"],
                "device":       ch["device"],
                "location":     ch["location"],
                "symbolicName": ch["symbolicName"],
                "field":        ch["field"],
                "valueType":    ch["valueType"],
            },
            "points": chunk,
        })
    except RuntimeError as e:
        if "already exists" in str(e) or "409" in str(e):
            print(f"  SKIP  channel {sym} (already has data)")
            return
        raise

    # Upload remaining chunks
    for start in range(200, len(pts), 200):
        sub = [{"timestamp": ts, "value": v} for ts, v in pts[start : start + 200]]
        _req("POST", url, headers, {
            "timeseries": {
                "measurement":  ch["measurement"],
                "device":       ch["device"],
                "location":     ch["location"],
                "symbolicName": ch["symbolicName"],
                "field":        ch["field"],
                "valueType":    ch["valueType"],
            },
            "points": sub,
        })
    print(f"  OK    channel {sym} ({len(pts)} points)")


# ── TS-IDc live demonstration ─────────────────────────────────────────────────

def run_demo(v2_base: str, headers: dict, tsc_id: int) -> None:
    """
    Exercise the two TS-IDc endpoints and print a comparison of the old
    5-tuple addressing vs the new single-UUID addressing.
    """
    h = {k: v for k, v in headers.items() if k != "Content-Type"}
    h["Accept"] = "application/json"

    print("\n" + "=" * 70)
    print("TS-IDc DEMO — single-key channel addressing")
    print("=" * 70)

    # Step 1: list channels → get shepardIds
    url_list = f"{v2_base}/v2/timeseries-containers/{tsc_id}/channels"
    print(f"\n① GET {url_list}")
    _, channels_data = _req("GET", url_list, h)
    assert isinstance(channels_data, list), f"expected list, got {type(channels_data)}"

    print(f"\n   Container {tsc_id} has {len(channels_data)} channels:")
    print(f"   {'symbolicName':<20} {'measurement':<14} {'shepardId':<38} {'legacy id'}")
    print(f"   {'-'*20} {'-'*14} {'-'*38} {'-'*10}")
    for ch in channels_data:
        print(
            f"   {ch['symbolicName']:<20} {ch['measurement']:<14} "
            f"{ch['shepardId']:<38} {ch['id']}"
        )

    # Step 2: fetch data by shepardId for the first channel
    ch0 = channels_data[0]
    now_ns  = int(time.time() * 1e9)
    back_ns = now_ns - 3600 * int(1e9)   # 1 h back
    url_data = (
        f"{v2_base}/v2/timeseries-containers/{tsc_id}"
        f"/channels/{ch0['shepardId']}/data"
        f"?start={back_ns}&end={now_ns}&downsample=lttb&max_points=50"
    )
    print(f"\n② GET …/channels/{ch0['shepardId'][:8]}…/data?downsample=lttb&max_points=50")
    print(f"   (channel: {ch0['symbolicName']}, full URL below)")
    print(f"   {url_data}")
    _, data_body = _req("GET", url_data, h)
    points = data_body.get("points") or []
    print(f"\n   Returned {len(points)} data points (LTTB, max 50).")
    if points:
        p0 = points[0]
        pN = points[-1]
        ts0 = datetime.fromtimestamp(p0["timestamp"] / 1e9, tz=timezone.utc).isoformat()
        tsN = datetime.fromtimestamp(pN["timestamp"] / 1e9, tz=timezone.utc).isoformat()
        print(f"   First: timestamp={ts0}  value={p0['value']}")
        print(f"   Last:  timestamp={tsN}  value={pN['value']}")

    print("\n③ Contrast: legacy 5-tuple vs new shepardId addressing")
    print(f"   v1 path:  GET /shepard/api/timeseriesContainers/{tsc_id}/timeseries")
    print(f"             ?measurement={ch0['measurement']}&device={ch0['device']}")
    print(f"             &location={ch0['location']}&symbolicName={ch0['symbolicName']}")
    print(f"             &field={ch0['field']}&start=...&end=...")
    print(f"   v2 path:  GET /v2/timeseries-containers/{tsc_id}")
    print(f"             /channels/{ch0['shepardId']}/data?start=...&end=...")

    print("\n   ✓ TS-IDc demo complete.")
    print("=" * 70 + "\n")


# ── reset ──────────────────────────────────────────────────────────────────────

def reset(v1: str, headers: dict) -> None:
    cid = _find_collection(v1, headers, COLLECTION_NAME)
    if cid is None:
        print("Nothing to reset.")
        return
    _req("DELETE", f"{v1}/collections/{cid}", headers, ok_statuses=(200, 204))
    print(f"Deleted collection id={cid}")


# ── main ───────────────────────────────────────────────────────────────────────

def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--host",   required=True, help="v1 base URL, e.g. https://host/shepard/api")
    p.add_argument("--apikey", required=True, help="API key (X-API-KEY header)")
    p.add_argument("--reset",  action="store_true", help="Delete and re-seed")
    p.add_argument("--demo",   action="store_true", help="Run the TS-IDc demo after seeding")
    args = p.parse_args()

    v1  = _v1(args.host)
    v2  = _v2(args.host)
    hdrs = {"X-API-KEY": args.apikey}

    if args.reset:
        print("── reset ──────────────────────────────────────")
        reset(v1, hdrs)

    print("── seed ───────────────────────────────────────")
    cid    = _ensure_collection(v1, hdrs)
    do_id  = _ensure_dataobject(v1, hdrs, cid)
    tsc_id = _ensure_tsc(v1, hdrs)

    # Reference time: place data at "1 hour ago" so it's within the default
    # query window of any v2 caller using ?start=now-1h&end=now.
    t0_ns   = int((time.time() - 3600) * 1e9)
    t_end_ns = t0_ns + int(DURATION_S * 1e9)

    # Seed channels first — the TS reference body requires non-empty timeseries list.
    for ch in CHANNELS:
        _seed_channel(v1, hdrs, tsc_id, ch, t0_ns)

    _ensure_ts_reference(v1, hdrs, cid, do_id, tsc_id, t0_ns, t_end_ns)

    print(f"\n  Dataset live: Collection id={cid}  DO id={do_id}  TSC id={tsc_id}")
    print(f"  UI: /collections/{cid}/dataobjects/{do_id}")

    if args.demo:
        run_demo(v2, hdrs, tsc_id)


if __name__ == "__main__":
    main()
