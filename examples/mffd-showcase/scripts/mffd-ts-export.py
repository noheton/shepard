# /// script
# requires-python = ">=3.11"
# dependencies = ["requests"]
# ///
#!/usr/bin/env python3
"""mffd-ts-export.py — Parallel full export from a Shepard collection (DLR cube3).

For each DataObject in the target collection(s) saves:
  • metadata.json        — full DO dict (name, attributes, parent/predecessor links)
  • ts/<ref>.csv         — ROW-format timeseries CSV per TimeseriesReference
  • files/<name>         — raw file payload per FileReference OID
  • structured/<ref>.json — decoded StructuredDataReference payload

Also saves:
  • <slug>/collection.json — collection-level metadata
  • <slug>/hierarchy.json  — parent/child/predecessor/successor graph by DO name

AFP tapelaying (coll_id=48297, ~8000 DOs) is the primary target.
Bridge welding is opt-in via INCLUDE_BRIDGEWELDING=1.

Usage:
    SOURCE_SHEPARD_URL=https://backend.bt-au-cube3.intra.dlr.de \\
    SOURCE_SHEPARD_API_KEY=<jwt> \\
    uv run python mffd-ts-export.py

    # Parallel workers (default 4, max 16):
    WORKERS=8 uv run python mffd-ts-export.py

    # Skip individual payload types:
    SKIP_FILES=1 SKIP_TS=1 SKIP_STRUCTURED=1 uv run python mffd-ts-export.py

    # Include bridge welding:
    INCLUDE_BRIDGEWELDING=1 uv run python mffd-ts-export.py

    # Resume an interrupted run — idempotent, skips existing non-empty files:
    uv run python mffd-ts-export.py

Output layout:
    ts-export/
    ├── manifest.json
    ├── tapelaying/
    │   ├── collection.json       ← collection metadata
    │   ├── hierarchy.json        ← parent/child/predecessor/successor graph
    │   └── <do_name>/
    │       ├── metadata.json     ← full DataObject JSON (attrs, links)
    │       ├── ts/<ref>.csv      ← ROW-format timeseries CSV
    │       ├── files/<name>      ← raw file payload
    │       └── structured/<ref>.json
    └── bridgewelding/
        └── ...
"""

import json
import os
import random
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import requests

# ── Config ────────────────────────────────────────────────────────────────────
# Defaults: DLR cube3 intranet (kreb_fl, jti eca5887a, minted 2026-05-23).
# Override via env if the JWT has expired and you've re-minted.

SOURCE_URL = os.environ.get(
    "SOURCE_SHEPARD_URL",
    "https://backend.bt-au-cube3.intra.dlr.de",
).rstrip("/")
SOURCE_KEY = os.environ.get(
    "SOURCE_SHEPARD_API_KEY",
    "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJrcmViX2ZsIiwiaXNzIjoiaHR0cDovL2JhY2tlbmQuYnQtYXUtY3ViZTMuaW50cmEuZGxyLmRlL3NoZXBhcmQvYXBpLyIsIm5iZiI6MTc3OTU0NDMxNSwiaWF0IjoxNzc5NTQ0MzE1LCJqdGkiOiJlY2E1ODg3YS1iZTUyLTRkMTktYTZjNS01ZDJkYjlmYzcxOGUifQ.SZNX9ne7Hbdu5bWyGsCRGdXM5kUa_R1oRfgNOHkmk5oAnGP8Uoss1dqKmFvwRiW29s-CAtD1wTc7nRq3ySlJQqakGffMWvNaNHk6PtLiBr8GYQ3kX6NY8a807bVbAcdbnO2HYfRFAT6kKMjIjmuLw16E6fZCRdVJq4J9otKu_tMN7qtprgusyoV5jZLZuXBGZIBdJAf9ucBAGoNBhi3KOI0Otonna5TK9KqMVygl2OFZa8ttvchl0j1JFevH-oC82WrvvTHEaiaeIdG37tNrzPFttiLswFMNYHXhhRORiX2d2oLRTvoQX3s9HBTRFiUFBgmwBhNoJTvJoUFdQBjA",
)

INCLUDE_BRIDGEWELDING = os.environ.get("INCLUDE_BRIDGEWELDING", "").lower() in ("1", "true", "yes")
SKIP_TS               = os.environ.get("SKIP_TS",         "").lower() in ("1", "true", "yes")
SKIP_FILES            = os.environ.get("SKIP_FILES",      "").lower() in ("1", "true", "yes")
SKIP_STRUCTURED       = os.environ.get("SKIP_STRUCTURED", "").lower() in ("1", "true", "yes")
SKIP_METADATA         = os.environ.get("SKIP_METADATA",   "").lower() in ("1", "true", "yes")

COLLECTIONS: dict[str, int] = {
    "tapelaying": int(os.environ.get("TAPELAYING_COLL_ID", 48297)),
}
if INCLUDE_BRIDGEWELDING:
    COLLECTIONS["bridgewelding"] = int(os.environ.get("BRIDGEWELDING_COLL_ID", 163811))

OUT_DIR         = Path(os.environ.get("OUT_DIR", "ts-export"))
PAGE_SIZE       = int(os.environ.get("PAGE_SIZE", "200"))
WORKERS         = min(int(os.environ.get("WORKERS", "4")), 16)
RETRY_MAX       = int(os.environ.get("RETRY_MAX", "10"))
RETRY_BASE      = float(os.environ.get("RETRY_BASE", "3.0"))
MANIFEST_FLUSH  = 20   # flush manifest to disk every N completed DOs

# ── Concurrency primitives ────────────────────────────────────────────────────

_thread_local  = threading.local()
_print_lock    = threading.Lock()
_manifest_lock = threading.Lock()
_shutdown      = threading.Event()   # set on 401 — workers drain gracefully

# Global DB-choke throttle: if 3+ workers hit 5xx within 10 s, pause 30 s.
_choke_lock       = threading.Lock()
_choke_hits: list[float] = []
_choke_pause      = threading.Event()
_choke_pause.set()   # initially unblocked (set = "you may proceed")
_CHOKE_WINDOW     = 10.0   # seconds
_CHOKE_THRESHOLD  = 3
_CHOKE_PAUSE_SECS = 30.0


def _record_5xx() -> None:
    """Called by any worker on each 5xx/429. Triggers global pause if threshold hit."""
    now = time.monotonic()
    with _choke_lock:
        _choke_hits.append(now)
        # purge old entries outside the window
        while _choke_hits and _choke_hits[0] < now - _CHOKE_WINDOW:
            _choke_hits.pop(0)
        if len(_choke_hits) >= _CHOKE_THRESHOLD and _choke_pause.is_set():
            _choke_pause.clear()
            _log(f"[THROTTLE] {_CHOKE_THRESHOLD}+ server errors in {_CHOKE_WINDOW:.0f}s "
                 f"— pausing all workers for {_CHOKE_PAUSE_SECS:.0f}s")

            def _resume():
                time.sleep(_CHOKE_PAUSE_SECS)
                _choke_hits.clear()
                _choke_pause.set()
                _log("[THROTTLE] Resuming workers.")

            threading.Thread(target=_resume, daemon=True).start()


def _wait_if_throttled() -> None:
    _choke_pause.wait()


def _log(msg: str) -> None:
    with _print_lock:
        print(msg, flush=True)


# ── Per-thread HTTP session ───────────────────────────────────────────────────

def _session() -> requests.Session:
    if not hasattr(_thread_local, "session"):
        s = requests.Session()
        s.headers.update({"X-API-KEY": SOURCE_KEY, "Accept": "application/json"})
        _thread_local.session = s
    return _thread_local.session


def _request(method: str, url: str, *, params=None, stream=False, timeout=60) -> requests.Response | None:
    for attempt in range(RETRY_MAX):
        if _shutdown.is_set():
            return None
        _wait_if_throttled()
        try:
            r = _session().request(method, url, params=params, stream=stream, timeout=timeout)
            if r.status_code == 401:
                _log("\n[FATAL] 401 Unauthorized — JWT expired. Re-mint and restart.")
                _shutdown.set()
                return None
            if r.status_code == 429 or r.status_code >= 500:
                jitter = random.uniform(0.8, 1.4)
                wait = RETRY_BASE * (2 ** attempt) * jitter
                _log(f"  [{r.status_code}] retry {attempt+1}/{RETRY_MAX} in {wait:.1f}s  {url}")
                _record_5xx()
                time.sleep(wait)
                continue
            if not r.ok:
                _log(f"  [WARN] {r.status_code}  {url}")
                return None
            return r
        except requests.RequestException as exc:
            jitter = random.uniform(0.8, 1.4)
            wait = RETRY_BASE * (2 ** attempt) * jitter
            _log(f"  [net-err] {exc} — retry {attempt+1}/{RETRY_MAX} in {wait:.1f}s")
            time.sleep(wait)
    _log(f"  [FAIL] gave up after {RETRY_MAX} attempts: {url}")
    return None


def _get(path: str, params=None, *, timeout=60) -> requests.Response | None:
    return _request("GET", SOURCE_URL + path, params=params, timeout=timeout)


def _get_stream(path: str, *, timeout=600) -> requests.Response | None:
    return _request("GET", SOURCE_URL + path, stream=True, timeout=timeout)


# ── API helpers ───────────────────────────────────────────────────────────────

def fetch_collection(coll_id: int) -> dict | None:
    r = _get(f"/shepard/api/collections/{coll_id}")
    return r.json() if r else None


def all_dos(coll_id: int) -> list[dict]:
    """Return all DataObjects in a collection (paginated, blocking)."""
    results = []
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
        results.extend(items)
        if len(items) < PAGE_SIZE:
            break
        page += 1
        if page % 10 == 0:
            _log(f"  ... fetched {len(results)} DOs so far (page {page})")
    return results


def ts_refs_for(coll_id: int, do_id: int) -> list[dict]:
    r = _get(f"/shepard/api/collections/{coll_id}/dataObjects/{do_id}/timeseriesReferences")
    return r.json() if r else []


def file_refs_for(coll_id: int, do_id: int) -> list[dict]:
    r = _get(f"/shepard/api/collections/{coll_id}/dataObjects/{do_id}/fileReferences")
    return r.json() if r else []


def structured_refs_for(coll_id: int, do_id: int) -> list[dict]:
    r = _get(f"/shepard/api/collections/{coll_id}/dataObjects/{do_id}/structuredDataReferences")
    return r.json() if r else []


def export_ts_csv(coll_id: int, do_id: int, ref_id: int) -> bytes | None:
    r = _get(
        f"/shepard/api/collections/{coll_id}/dataObjects/{do_id}"
        f"/timeseriesReferences/{ref_id}/export",
        {"csv_format": "ROW"},
        timeout=300,
    )
    if r is None:
        return None
    content = r.content
    return content if content and len(content) >= 8 else None


def download_file_payload(coll_id: int, do_id: int, fref_id: int, oid: str, dest: Path) -> bool:
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


def download_structured_payload(coll_id: int, do_id: int, ref_id: int) -> list[dict] | None:
    r = _get(
        f"/shepard/api/collections/{coll_id}/dataObjects/{do_id}"
        f"/structuredDataReferences/{ref_id}/payload",
        timeout=120,
    )
    if r is None:
        return None
    try:
        items = r.json()
    except Exception:
        return None
    if not items:
        return None

    result = []
    for item in items:
        name = (item.get("structuredData") or {}).get("name") or "unknown"
        raw_payload = item.get("payload")
        if raw_payload is None:
            continue
        # Bug E: payload field is a JSON-encoded string — decode the inner value.
        try:
            decoded = json.loads(raw_payload) if isinstance(raw_payload, str) else raw_payload
        except (json.JSONDecodeError, TypeError):
            decoded = raw_payload
        result.append({"name": name, "data": decoded})

    return result if result else None


# ── Helpers ───────────────────────────────────────────────────────────────────

def safe_name(s: str) -> str:
    return "".join(c if c.isalnum() or c in "-_." else "_" for c in str(s))


def path_safe(s: str) -> str:
    """Preserve original filename; strip only path-traversal chars."""
    s = str(s).replace("/", "_").replace("\\", "_").replace("\x00", "")
    s = s.lstrip(".")  # no leading dots (hidden files / dotfiles)
    return s or "unnamed"


def fmt_bytes(n: int) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024:
            return f"{n:.0f}{unit}"
        n //= 1024
    return f"{n:.0f}TB"


# ── Per-DO worker ─────────────────────────────────────────────────────────────

def process_do(
    slug: str,
    coll_id: int,
    coll_dir: Path,
    do: dict,
    coll_manifest_dos: dict,
    totals: dict,
    totals_lock: threading.Lock,
) -> None:
    if _shutdown.is_set():
        return

    do_id   = do["id"]
    do_name = safe_name(do.get("name") or f"do-{do_id}")
    do_dir  = coll_dir / do_name

    with _manifest_lock:
        do_manifest = coll_manifest_dos.setdefault(do_name, {
            "do_id":          do_id,
            "do_name":        do.get("name"),
            "ts_refs":        [],
            "file_refs":      [],
            "structured_refs": [],
        })

    # ── DataObject metadata ───────────────────────────────────────────────────
    if not SKIP_METADATA:
        meta_path = do_dir / "metadata.json"
        if not meta_path.exists():
            do_dir.mkdir(parents=True, exist_ok=True)
            with meta_path.open("w") as f:
                json.dump(do, f, indent=2)

    # ── Timeseries ────────────────────────────────────────────────────────────
    if not SKIP_TS:
        refs = ts_refs_for(coll_id, do_id)
        with _manifest_lock:
            existing_ts_ids = {e["ref_id"] for e in do_manifest.get("ts_refs", [])}
        ts_dir = coll_dir / do_name / "ts"

        for ref in refs:
            if _shutdown.is_set():
                return
            ref_id   = ref["id"]
            ref_name = safe_name(ref.get("name") or f"ts-{ref_id}")
            container_id = ref.get("timeseriesContainerId") or 0
            csv_path = ts_dir / f"{ref_name}.csv"

            if csv_path.exists() and ref_id in existing_ts_ids:
                with totals_lock:
                    totals["ts_skipped"] += 1
                continue

            _log(f"  [ts] {do_name}/{ref_name} ...")
            csv_bytes = export_ts_csv(coll_id, do_id, ref_id)
            if csv_bytes is None:
                with totals_lock:
                    totals["ts_empty"] += 1
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
            _log(f"  [ts] {do_name}/{ref_name} — {row_count} rows, {len(channel_list)} channels")

            with _manifest_lock:
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
            with totals_lock:
                totals["ts_exported"] += 1

    # ── File references ───────────────────────────────────────────────────────
    if not SKIP_FILES:
        frefs = file_refs_for(coll_id, do_id)
        with _manifest_lock:
            existing_file_keys = {e["key"] for e in do_manifest.get("file_refs", [])}
        files_dir = coll_dir / do_name / "files"

        for fref in frefs:
            if _shutdown.is_set():
                return
            fref_id   = fref["id"]
            base_name = fref.get("name") or fref.get("fileName") or f"file-{fref_id}"
            oids      = fref.get("fileOids") or []

            entries = (
                [(str(oid), base_name if len(oids) == 1 else f"{base_name}.{i}")
                 for i, oid in enumerate(oids)]
                if oids else [("", base_name)]
            )

            for oid, fname in entries:
                if _shutdown.is_set():
                    return
                key  = f"{fref_id}:{oid}"
                dest = files_dir / path_safe(fname)

                if dest.exists() and dest.stat().st_size > 0 and key in existing_file_keys:
                    with totals_lock:
                        totals["file_skipped"] += 1
                    continue

                _log(f"  [file] {do_name}/files/{fname} ...")
                ok = download_file_payload(coll_id, do_id, fref_id, oid, dest)
                if not ok:
                    with totals_lock:
                        totals["file_empty"] += 1
                    continue

                size_bytes = dest.stat().st_size
                _log(f"  [file] {do_name}/files/{fname} — {fmt_bytes(size_bytes)}")

                with _manifest_lock:
                    if key not in existing_file_keys:
                        do_manifest["file_refs"].append({
                            "key":      key,
                            "fref_id":  fref_id,
                            "ref_name": base_name,
                            "oid":      oid,
                            "file":     f"{slug}/{do_name}/files/{path_safe(fname)}",
                            "size":     size_bytes,
                        })
                        existing_file_keys.add(key)
                with totals_lock:
                    totals["file_exported"] += 1

    # ── Structured data references ────────────────────────────────────────────
    if not SKIP_STRUCTURED:
        srefs = structured_refs_for(coll_id, do_id)
        with _manifest_lock:
            existing_sref_ids = {e["ref_id"] for e in do_manifest.get("structured_refs", [])}
        struct_dir = coll_dir / do_name / "structured"

        for sref in srefs:
            if _shutdown.is_set():
                return
            sref_id  = sref["id"]
            ref_name = safe_name(sref.get("name") or f"struct-{sref_id}")
            out_path = struct_dir / f"{ref_name}.json"

            if out_path.exists() and out_path.stat().st_size > 0 and sref_id in existing_sref_ids:
                with totals_lock:
                    totals["struct_skipped"] += 1
                continue

            _log(f"  [struct] {do_name}/structured/{ref_name} ...")
            payload = download_structured_payload(coll_id, do_id, sref_id)
            if payload is None:
                with totals_lock:
                    totals["struct_empty"] += 1
                continue

            struct_dir.mkdir(parents=True, exist_ok=True)
            with out_path.open("w") as f:
                json.dump(payload, f, indent=2)

            row_count = sum(
                len(p["data"]) if isinstance(p["data"], list) else 1
                for p in payload
            )
            _log(f"  [struct] {do_name}/structured/{ref_name} — {len(payload)} table(s), ~{row_count} rows")

            with _manifest_lock:
                if sref_id not in existing_sref_ids:
                    do_manifest["structured_refs"].append({
                        "ref_id":   sref_id,
                        "ref_name": sref.get("name") or f"struct-{sref_id}",
                        "file":     f"{slug}/{do_name}/structured/{ref_name}.json",
                    })
                    existing_sref_ids.add(sref_id)
            with totals_lock:
                totals["struct_exported"] += 1


# ── Hierarchy builder ─────────────────────────────────────────────────────────

def build_hierarchy(dos: list[dict]) -> dict:
    """Build id→name map then emit name-keyed parent/child/predecessor graph."""
    id_to_name: dict[int, str] = {
        do["id"]: safe_name(do.get("name") or f"do-{do['id']}")
        for do in dos
    }

    def resolve_ids(ids):
        return [id_to_name[i] for i in (ids or []) if i in id_to_name]

    result = {}
    for do in dos:
        name = id_to_name[do["id"]]
        result[name] = {
            "do_id":        do["id"],
            "parent":       id_to_name.get(do.get("parentId")),
            "children":     resolve_ids(do.get("childrenIds")),
            "predecessors": resolve_ids(do.get("predecessorIds")),
            "successors":   resolve_ids(do.get("successorIds")),
        }
    return result


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    manifest_path = OUT_DIR / "manifest.json"

    if manifest_path.exists():
        with manifest_path.open() as f:
            manifest = json.load(f)
        _log(f"Resuming — loaded existing manifest ({manifest_path})")
    else:
        manifest = {
            "exported_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "source_url":  SOURCE_URL,
            "collections": {},
        }

    totals = {
        "dos": 0,
        "ts_exported": 0,     "ts_skipped": 0,     "ts_empty": 0,
        "file_exported": 0,   "file_skipped": 0,   "file_empty": 0,
        "struct_exported": 0, "struct_skipped": 0, "struct_empty": 0,
    }
    totals_lock = threading.Lock()

    for slug, coll_id in COLLECTIONS.items():
        coll_dir = OUT_DIR / slug
        coll_dir.mkdir(exist_ok=True)

        _log(f"\n{'='*64}")
        _log(f"Collection: {slug}  (id={coll_id})")
        _log(f"{'='*64}")

        # ── Collection metadata ───────────────────────────────────────────────
        coll_meta_path = coll_dir / "collection.json"
        if not coll_meta_path.exists():
            _log(f"Fetching collection metadata ...")
            coll_obj = fetch_collection(coll_id)
            if coll_obj:
                with coll_meta_path.open("w") as f:
                    json.dump(coll_obj, f, indent=2)
                _log(f"  Saved collection.json  ({coll_obj.get('name', '?')})")
            else:
                _log(f"  [WARN] Could not fetch collection metadata")

        # ── Fetch all DO stubs (serial — pagination can't be parallelised) ───
        _log(f"Fetching DataObject list ...")
        all_do_list = all_dos(coll_id)
        _log(f"  {len(all_do_list)} DataObjects found")

        if _shutdown.is_set():
            _log("[ABORT] 401 received during DO fetch — exiting.")
            sys.exit(2)

        # ── Hierarchy JSON ────────────────────────────────────────────────────
        hier_path = coll_dir / "hierarchy.json"
        _log("Building hierarchy.json ...")
        hierarchy = build_hierarchy(all_do_list)
        with hier_path.open("w") as f:
            json.dump(hierarchy, f, indent=2)
        _log(f"  Saved hierarchy.json  ({len(hierarchy)} nodes)")

        # ── Parallel DO processing ────────────────────────────────────────────
        coll_manifest = manifest["collections"].setdefault(slug, {"id": coll_id, "dos": {}})
        coll_manifest_dos = coll_manifest["dos"]

        completed = 0
        _log(f"Processing {len(all_do_list)} DOs with {WORKERS} workers ...")

        with ThreadPoolExecutor(max_workers=WORKERS) as executor:
            future_to_name = {
                executor.submit(
                    process_do,
                    slug, coll_id, coll_dir, do,
                    coll_manifest_dos, totals, totals_lock,
                ): safe_name(do.get("name") or f"do-{do['id']}")
                for do in all_do_list
            }

            for future in as_completed(future_to_name):
                if _shutdown.is_set():
                    break
                do_name = future_to_name[future]
                try:
                    future.result()
                except Exception as exc:
                    _log(f"  [ERROR] {do_name}: {exc}")

                completed += 1
                with totals_lock:
                    totals["dos"] += 1

                # Periodic manifest flush
                if completed % MANIFEST_FLUSH == 0:
                    with _manifest_lock:
                        manifest["exported_at"] = time.strftime(
                            "%Y-%m-%dT%H:%M:%SZ", time.gmtime()
                        )
                        with manifest_path.open("w") as f:
                            json.dump(manifest, f, indent=2)
                    _log(
                        f"  ... {completed}/{len(all_do_list)} DOs  "
                        f"ts={totals['ts_exported']}  "
                        f"files={totals['file_exported']}  "
                        f"struct={totals['struct_exported']}"
                    )

        if _shutdown.is_set():
            _log("[ABORT] 401 received — exiting.")
            sys.exit(2)

        _log(f"\nDone {slug}: {completed} DOs processed")

    # Final manifest flush
    with _manifest_lock:
        manifest["exported_at"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        with manifest_path.open("w") as f:
            json.dump(manifest, f, indent=2)

    _log(f"""
{'='*64}
Export complete
  DOs traversed  : {totals['dos']}

  Timeseries
    exported     : {totals['ts_exported']}
    skipped      : {totals['ts_skipped']}  (already present)
    empty        : {totals['ts_empty']}    (0-byte / no data)

  Files
    exported     : {totals['file_exported']}
    skipped      : {totals['file_skipped']}  (already present)
    empty/error  : {totals['file_empty']}

  Structured data
    exported     : {totals['struct_exported']}
    skipped      : {totals['struct_skipped']}  (already present)
    empty        : {totals['struct_empty']}

  Manifest       : {manifest_path}
{'='*64}
""")


if __name__ == "__main__":
    main()
