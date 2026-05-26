# /// script
# requires-python = ">=3.11"
# dependencies = ["requests"]
# ///
#!/usr/bin/env python3
"""mffd-ts-export.py — Export all timeseries + file references from DLR cube3.

Traverses the AFP tapelaying collection (coll_id=48297, ~8000 DOs), fetches
every TimeseriesReference and FileReference per DataObject, and downloads all
payloads. Output is keyed by DataObject name so data can be associated after
ID churn.

Usage (on DLR cube3 or any host with DLR intranet access):
    SOURCE_SHEPARD_URL=https://backend.bt-au-cube3.intra.dlr.de \\
    SOURCE_SHEPARD_API_KEY=<jwt> \\
    uv run python mffd-ts-export.py

    # Also include bridge welding:
    INCLUDE_BRIDGEWELDING=1 uv run python mffd-ts-export.py

    # Skip file download (TS only):
    SKIP_FILES=1 uv run python mffd-ts-export.py

    # Skip TS download (files only):
    SKIP_TS=1 uv run python mffd-ts-export.py

    # Resume an interrupted run — idempotent, skips existing non-empty files.
    uv run python mffd-ts-export.py

Output layout:
    ts-export/
    ├── manifest.json              ← DO name → refs + channel list
    ├── tapelaying/
    │   └── <do_name>/
    │       ├── ts/
    │       │   └── <ref_name>.csv     ← ROW-format timeseries CSV
    │       └── files/
    │           └── <filename>         ← raw file payload (name from ref)
    └── bridgewelding/
        └── ...
"""

import json
import os
import sys
import time
from pathlib import Path

import requests

# ── Config ────────────────────────────────────────────────────────────────────

SOURCE_URL            = os.environ["SOURCE_SHEPARD_URL"].rstrip("/")
SOURCE_KEY            = os.environ["SOURCE_SHEPARD_API_KEY"]
INCLUDE_BRIDGEWELDING = os.environ.get("INCLUDE_BRIDGEWELDING", "").lower() in ("1", "true", "yes")
SKIP_TS               = os.environ.get("SKIP_TS",    "").lower() in ("1", "true", "yes")
SKIP_FILES            = os.environ.get("SKIP_FILES", "").lower() in ("1", "true", "yes")

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


def _request(method: str, url: str, *, params=None, stream=False, timeout=60) -> requests.Response | None:
    for attempt in range(RETRY_MAX):
        try:
            r = SESSION.request(method, url, params=params, stream=stream, timeout=timeout)
            if r.status_code == 401:
                print(f"\n[FATAL] 401 Unauthorized — JWT expired. Re-mint and restart.", flush=True)
                sys.exit(2)
            if r.status_code == 429 or r.status_code >= 500:
                wait = RETRY_BASE * (2 ** attempt)
                print(f"  [{r.status_code}] retry in {wait:.0f}s  {url}", flush=True)
                time.sleep(wait)
                continue
            if not r.ok:
                print(f"  [WARN] {r.status_code}  {url}", flush=True)
                return None
            return r
        except requests.RequestException as exc:
            wait = RETRY_BASE * (2 ** attempt)
            print(f"  [net-err] {exc} — retry in {wait:.0f}s", flush=True)
            time.sleep(wait)
    print(f"  [FAIL] gave up after {RETRY_MAX} attempts: {url}", flush=True)
    return None


def _get(path: str, params=None, *, timeout=60) -> requests.Response | None:
    return _request("GET", SOURCE_URL + path, params=params, timeout=timeout)


def _get_stream(path: str, *, timeout=600) -> requests.Response | None:
    return _request("GET", SOURCE_URL + path, stream=True, timeout=timeout)


# ── v5 API traversal ──────────────────────────────────────────────────────────

def all_dos(coll_id: int):
    """Yield every DataObject in a collection (paginated)."""
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
    r = _get(f"/shepard/api/collections/{coll_id}/dataObjects/{do_id}/timeseriesReferences")
    return r.json() if r else []


def file_refs_for(coll_id: int, do_id: int) -> list[dict]:
    r = _get(f"/shepard/api/collections/{coll_id}/dataObjects/{do_id}/fileReferences")
    return r.json() if r else []


def export_ts_csv(coll_id: int, do_id: int, ref_id: int) -> bytes | None:
    """Export timeseries reference as ROW-format CSV (preserves native sampling)."""
    r = _get(
        f"/shepard/api/collections/{coll_id}/dataObjects/{do_id}"
        f"/timeseriesReferences/{ref_id}/export",
        {"csv_format": "ROW"},
        timeout=300,
    )
    if r is None:
        return None
    content = r.content
    if not content or len(content) < 8:
        return None
    return content


def download_file_payload(coll_id: int, do_id: int, fref_id: int, oid: str, dest: Path) -> bool:
    """Stream a single file payload to disk. Returns True on success."""
    if oid:
        path = (f"/shepard/api/collections/{coll_id}/dataObjects/{do_id}"
                f"/fileReferences/{fref_id}/payload/{oid}")
    else:
        path = (f"/shepard/api/collections/{coll_id}/dataObjects/{do_id}"
                f"/fileReferences/{fref_id}/payload")

    r = _get_stream(path)
    if r is None:
        return False

    dest.parent.mkdir(parents=True, exist_ok=True)
    size = 0
    with dest.open("wb") as fh:
        for chunk in r.iter_content(chunk_size=256 * 1024):
            if chunk:
                fh.write(chunk)
                size += len(chunk)

    if size == 0:
        dest.unlink(missing_ok=True)
        return False
    return True


# ── Helpers ───────────────────────────────────────────────────────────────────

def safe_name(s: str) -> str:
    return "".join(c if c.isalnum() or c in "-_." else "_" for c in str(s))


def fmt_bytes(n: int) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024:
            return f"{n:.0f}{unit}"
        n //= 1024
    return f"{n:.0f}TB"


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    manifest_path = OUT_DIR / "manifest.json"

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

    totals = {
        "dos": 0,
        "ts_exported": 0, "ts_skipped": 0, "ts_empty": 0,
        "file_exported": 0, "file_skipped": 0, "file_empty": 0,
    }

    for slug, coll_id in COLLECTIONS.items():
        coll_dir = OUT_DIR / slug
        coll_dir.mkdir(exist_ok=True)

        coll_manifest = manifest["collections"].setdefault(slug, {"id": coll_id, "dos": {}})

        print(f"\n{'='*64}", flush=True)
        print(f"Collection: {slug}  (id={coll_id})", flush=True)
        print(f"{'='*64}", flush=True)

        do_count = 0
        for do in all_dos(coll_id):
            do_id   = do["id"]
            do_name = safe_name(do.get("name") or f"do-{do_id}")
            do_count += 1
            totals["dos"] += 1

            do_manifest = coll_manifest["dos"].setdefault(do_name, {
                "do_id": do_id,
                "do_name": do.get("name"),
                "ts_refs": [],
                "file_refs": [],
            })

            # ── Timeseries ────────────────────────────────────────────────────
            if not SKIP_TS:
                refs = ts_refs_for(coll_id, do_id)
                existing_ts_ids = {e["ref_id"] for e in do_manifest.get("ts_refs", [])}
                ts_dir = coll_dir / do_name / "ts"

                for ref in refs:
                    ref_id   = ref["id"]
                    ref_name = safe_name(ref.get("name") or f"ts-{ref_id}")
                    container_id = ref.get("timeseriesContainerId") or 0
                    csv_path = ts_dir / f"{ref_name}.csv"

                    if csv_path.exists() and ref_id in existing_ts_ids:
                        totals["ts_skipped"] += 1
                        continue

                    print(f"  [ts] {do_name}/{ref_name} ... ", end="", flush=True)
                    csv_bytes = export_ts_csv(coll_id, do_id, ref_id)
                    if csv_bytes is None:
                        totals["ts_empty"] += 1
                        print("empty", flush=True)
                        continue

                    ts_dir.mkdir(parents=True, exist_ok=True)
                    csv_path.write_bytes(csv_bytes)

                    try:
                        rows_data = [
                            line.decode("utf-8", errors="replace").split(",")
                            for line in csv_bytes.split(b"\n")[1:] if line.strip()
                        ]
                        channels = sorted({
                            (r[0], r[1], r[2], r[3], r[4])
                            for r in rows_data if len(r) >= 5
                        })
                        channel_list = [
                            {"measurement": c[0], "device": c[1], "location": c[2],
                             "symbolicName": c[3], "field": c[4]}
                            for c in channels
                        ]
                    except Exception:
                        channel_list = []

                    row_count = max(0, csv_bytes.count(b"\n") - 1)
                    print(f"{row_count} rows, {len(channel_list)} channels", flush=True)

                    if ref_id not in existing_ts_ids:
                        do_manifest["ts_refs"].append({
                            "ref_id":       ref_id,
                            "ref_name":     ref.get("name") or f"ts-{ref_id}",
                            "container_id": container_id,
                            "file":         f"{slug}/{do_name}/ts/{ref_name}.csv",
                            "rows":         row_count,
                            "channels":     channel_list,
                        })
                        existing_ts_ids.add(ref_id)
                    totals["ts_exported"] += 1

            # ── File references ───────────────────────────────────────────────
            if not SKIP_FILES:
                frefs = file_refs_for(coll_id, do_id)
                existing_file_keys = {e["key"] for e in do_manifest.get("file_refs", [])}
                files_dir = coll_dir / do_name / "files"

                for fref in frefs:
                    fref_id   = fref["id"]
                    base_name = fref.get("name") or fref.get("fileName") or f"file-{fref_id}"
                    oids      = fref.get("fileOids") or []

                    # Normalise: one entry per OID (or one bare entry if no OIDs)
                    entries = (
                        [(str(oid), base_name if len(oids) == 1 else f"{base_name}.{i}")
                         for i, oid in enumerate(oids)]
                        if oids else [("", base_name)]
                    )

                    for oid, fname in entries:
                        key      = f"{fref_id}:{oid}"
                        dest     = files_dir / safe_name(fname)

                        if dest.exists() and dest.stat().st_size > 0 and key in existing_file_keys:
                            totals["file_skipped"] += 1
                            continue

                        print(f"  [file] {do_name}/files/{fname} ... ", end="", flush=True)
                        ok = download_file_payload(coll_id, do_id, fref_id, oid, dest)
                        if not ok:
                            totals["file_empty"] += 1
                            print("empty/error", flush=True)
                            continue

                        size_bytes = dest.stat().st_size
                        print(fmt_bytes(size_bytes), flush=True)

                        if key not in existing_file_keys:
                            do_manifest["file_refs"].append({
                                "key":      key,
                                "fref_id":  fref_id,
                                "ref_name": base_name,
                                "oid":      oid,
                                "file":     f"{slug}/{do_name}/files/{safe_name(fname)}",
                                "size":     size_bytes,
                            })
                            existing_file_keys.add(key)
                        totals["file_exported"] += 1

            # Persist manifest after every DO — survives interruption
            with manifest_path.open("w") as f:
                json.dump(manifest, f, indent=2)

            if do_count % 100 == 0:
                print(
                    f"  ... {do_count} DOs  "
                    f"ts={totals['ts_exported']}  files={totals['file_exported']}",
                    flush=True,
                )

        print(f"\nDone {slug}: {do_count} DOs", flush=True)

    manifest["exported_at"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    with manifest_path.open("w") as f:
        json.dump(manifest, f, indent=2)

    print(f"""
{'='*64}
Export complete
  DOs traversed  : {totals['dos']}

  Timeseries
    exported     : {totals['ts_exported']}
    skipped      : {totals['ts_skipped']}  (already present)
    empty        : {totals['ts_empty']}    (0-byte placeholder)

  Files
    exported     : {totals['file_exported']}
    skipped      : {totals['file_skipped']}  (already present)
    empty/error  : {totals['file_empty']}

  Manifest       : {manifest_path}
{'='*64}
""", flush=True)


if __name__ == "__main__":
    main()
