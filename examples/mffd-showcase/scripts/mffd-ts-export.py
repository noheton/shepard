# /// script
# requires-python = ">=3.11"
# dependencies = ["requests", "tqdm"]
# ///
#!/usr/bin/env python3
"""mffd-ts-export.py — Export all timeseries from DLR cube3 MFFD collections.

Traverses both MFFD source collections (tapelaying + bridgewelding), fetches
every TimeseriesReference per DataObject, and exports the raw data as CSV.
Output is keyed by DataObject name so the data can be associated later even
after IDs change.

Scope: AFP tapelaying collection (coll_id=48297, ~8000 DOs under
dataset/1-Tapelaying/mffd-tapelaying). Bridge welding is excluded by default
(set INCLUDE_BRIDGEWELDING=1 to add it).

Usage (on DLR cube3 or any host with DLR intranet access):
    SOURCE_SHEPARD_URL=https://backend.bt-au-cube3.intra.dlr.de \\
    SOURCE_SHEPARD_API_KEY=<jwt> \\
    uv run python mffd-ts-export.py

    # Also include bridge welding:
    INCLUDE_BRIDGEWELDING=1 uv run python mffd-ts-export.py

    # Resume an interrupted run (skips already-exported files):
    uv run python mffd-ts-export.py   # idempotent — skips existing CSVs

Output layout:
    ts-export/
    ├── manifest.json              ← stable index: DO name → refs + channel list
    ├── tapelaying/
    │   └── <do_name>/
    │       └── <ref_name>.csv     ← ROW-format timeseries CSV
    └── bridgewelding/
        └── <do_name>/
            └── <ref_name>.csv
"""

import json
import os
import sys
import time
from pathlib import Path

import requests

# ── Config ────────────────────────────────────────────────────────────────────

SOURCE_URL              = os.environ["SOURCE_SHEPARD_URL"].rstrip("/")
SOURCE_KEY              = os.environ["SOURCE_SHEPARD_API_KEY"]
INCLUDE_BRIDGEWELDING   = os.environ.get("INCLUDE_BRIDGEWELDING", "").lower() in ("1", "true", "yes")

# AFP tapelaying is the primary target (~8000 DOs, dataset/1-Tapelaying/mffd-tapelaying)
COLLECTIONS: dict[str, int] = {
    "tapelaying": int(os.environ.get("TAPELAYING_COLL_ID", 48297)),
}
if INCLUDE_BRIDGEWELDING:
    COLLECTIONS["bridgewelding"] = int(os.environ.get("BRIDGEWELDING_COLL_ID", 163811))

OUT_DIR    = Path(os.environ.get("OUT_DIR", "ts-export"))
PAGE_SIZE  = 200
RETRY_MAX  = 5
RETRY_BASE = 2.0   # seconds, doubled each attempt

# ── HTTP helpers ──────────────────────────────────────────────────────────────

SESSION = requests.Session()
SESSION.headers.update({"X-API-KEY": SOURCE_KEY, "Accept": "application/json"})


def _get(path: str, params: dict | None = None, *, timeout: int = 60) -> requests.Response | None:
    url = SOURCE_URL + path
    for attempt in range(RETRY_MAX):
        try:
            r = SESSION.get(url, params=params, timeout=timeout)
            if r.status_code == 401:
                print(f"\n[FATAL] 401 Unauthorized — JWT expired. Re-mint and restart.", flush=True)
                sys.exit(2)
            if r.status_code == 429 or r.status_code >= 500:
                wait = RETRY_BASE * (2 ** attempt)
                print(f"  [{r.status_code}] {url} — retry in {wait:.0f}s", flush=True)
                time.sleep(wait)
                continue
            if not r.ok:
                print(f"  [WARN] {r.status_code} {url}", flush=True)
                return None
            return r
        except requests.RequestException as exc:
            wait = RETRY_BASE * (2 ** attempt)
            print(f"  [net-err] {exc} — retry in {wait:.0f}s", flush=True)
            time.sleep(wait)
    print(f"  [FAIL] gave up after {RETRY_MAX} attempts: {url}", flush=True)
    return None


# ── v5 API traversal ──────────────────────────────────────────────────────────

def all_dos(coll_id: int):
    """Yield every DataObject in a collection (paginated). Yields raw dicts."""
    page = 0
    while True:
        r = _get(
            f"/shepard/api/collections/{coll_id}/dataObjects",
            {"page": str(page), "size": str(PAGE_SIZE)},
        )
        if r is None:
            break
        items = r.json()
        if not items:
            break
        yield from items
        if len(items) < PAGE_SIZE:
            break
        page += 1


def ts_refs_for(coll_id: int, do_id: int) -> list[dict]:
    """Return list of timeseriesReference dicts for a DataObject."""
    r = _get(f"/shepard/api/collections/{coll_id}/dataObjects/{do_id}/timeseriesReferences")
    if r is None:
        return []
    return r.json()


def container_channels(container_id: int) -> list[dict]:
    """Return timeseries channel tuples for a container (measurement/device/location/symbolicName/field)."""
    r = _get(f"/shepard/api/timeseriesContainers/{container_id}/timeseries")
    if r is None:
        return []
    return r.json()


def export_ts_csv(coll_id: int, do_id: int, ref_id: int) -> bytes | None:
    """Export timeseries reference as ROW-format CSV."""
    r = _get(
        f"/shepard/api/collections/{coll_id}/dataObjects/{do_id}"
        f"/timeseriesReferences/{ref_id}/export",
        {"csv_format": "ROW"},
        timeout=300,
    )
    if r is None:
        return None
    content = r.content
    # empty body = placeholder container
    if not content or len(content) < 8:
        return None
    return content


# ── Main ──────────────────────────────────────────────────────────────────────

def safe_name(s: str) -> str:
    """Make a string safe for use as a directory/file name."""
    return "".join(c if c.isalnum() or c in "-_." else "_" for c in str(s))


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    manifest_path = OUT_DIR / "manifest.json"

    # Load existing manifest if resuming
    if manifest_path.exists():
        with manifest_path.open() as f:
            manifest = json.load(f)
        print(f"Resuming — loaded existing manifest ({manifest_path})", flush=True)
    else:
        manifest = {
            "exported_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "source_url": SOURCE_URL,
            "collections": {},
        }

    totals = {"dos": 0, "refs": 0, "exported": 0, "skipped": 0, "empty": 0}

    for slug, coll_id in COLLECTIONS.items():
        if ONLY and ONLY != slug:
            continue

        coll_dir = OUT_DIR / slug
        coll_dir.mkdir(exist_ok=True)

        coll_manifest = manifest["collections"].setdefault(slug, {"id": coll_id, "dos": {}})

        print(f"\n{'='*60}", flush=True)
        print(f"Collection: {slug}  (id={coll_id})", flush=True)
        print(f"{'='*60}", flush=True)

        do_count = 0
        for do in all_dos(coll_id):
            do_id   = do["id"]
            do_name = safe_name(do.get("name") or f"do-{do_id}")
            do_count += 1
            totals["dos"] += 1

            refs = ts_refs_for(coll_id, do_id)
            if not refs:
                continue

            do_dir = coll_dir / do_name
            do_manifest = coll_manifest["dos"].setdefault(do_name, {
                "do_id": do_id,
                "do_name": do.get("name"),
                "ts_refs": [],
            })
            # Index existing refs by ref_id for resume
            existing_ref_ids = {entry["ref_id"] for entry in do_manifest.get("ts_refs", [])}

            for ref in refs:
                ref_id   = ref["id"]
                ref_name = safe_name(ref.get("name") or f"ts-{ref_id}")
                container_id = ref.get("timeseriesContainerId") or 0

                totals["refs"] += 1

                csv_path = do_dir / f"{ref_name}.csv"

                # Resume: skip if already exported
                if csv_path.exists() and ref_id in existing_ref_ids:
                    totals["skipped"] += 1
                    print(f"  [skip] {do_name}/{ref_name}.csv", flush=True)
                    continue

                print(f"  [export] {do_name}/{ref_name} (ref={ref_id}, ctr={container_id}) ... ",
                      end="", flush=True)

                csv_bytes = export_ts_csv(coll_id, do_id, ref_id)
                if csv_bytes is None:
                    totals["empty"] += 1
                    print("empty", flush=True)
                    continue

                do_dir.mkdir(exist_ok=True)
                csv_path.write_bytes(csv_bytes)

                # Parse header row for channel list
                try:
                    header_line = csv_bytes.split(b"\n")[0].decode("utf-8", errors="replace")
                    # ROW format: measurement,device,location,symbolicName,field,timestamp,value
                    # Channel identity is the unique (measurement,device,location,symbolicName,field)
                    rows = [line.decode("utf-8", errors="replace").split(",")
                            for line in csv_bytes.split(b"\n")[1:] if line.strip()]
                    channels = list({
                        (r[0], r[1], r[2], r[3], r[4])
                        for r in rows if len(r) >= 5
                    })
                    channel_list = [
                        {"measurement": c[0], "device": c[1], "location": c[2],
                         "symbolicName": c[3], "field": c[4]}
                        for c in sorted(channels)
                    ]
                except Exception:
                    channel_list = []

                row_count = max(0, csv_bytes.count(b"\n") - 1)
                print(f"{row_count} rows, {len(channel_list)} channels", flush=True)

                # Add to manifest (avoid duplicates on resume)
                if ref_id not in existing_ref_ids:
                    do_manifest["ts_refs"].append({
                        "ref_id":       ref_id,
                        "ref_name":     ref.get("name") or f"ts-{ref_id}",
                        "container_id": container_id,
                        "file":         f"{slug}/{do_name}/{ref_name}.csv",
                        "rows":         row_count,
                        "channels":     channel_list,
                    })
                    existing_ref_ids.add(ref_id)

                totals["exported"] += 1

            # Persist manifest after each DO so progress survives interruptions
            with manifest_path.open("w") as f:
                json.dump(manifest, f, indent=2)

            if do_count % 100 == 0:
                print(f"  ... {do_count} DOs processed so far", flush=True)

        print(f"\nDone {slug}: {do_count} DOs traversed", flush=True)

    # Final manifest write
    manifest["exported_at"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    with manifest_path.open("w") as f:
        json.dump(manifest, f, indent=2)

    print(f"""
{'='*60}
Export complete
  DOs traversed : {totals['dos']}
  TS refs found : {totals['refs']}
  Exported      : {totals['exported']}
  Skipped       : {totals['skipped']}  (already present — resume)
  Empty/skip    : {totals['empty']}    (0-byte placeholder containers)
  Manifest      : {manifest_path}
{'='*60}
""", flush=True)


if __name__ == "__main__":
    main()
