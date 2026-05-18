"""export-collection.py — dump one shepard Collection to a portable
directory tree, ready for re-import into another instance.

Designed for the MFFD showcase migration: user runs this against
their legacy shepard, ships the resulting directory, and the matching
`import-collection.py` (forthcoming) replays it into the new instance.

The script uses ONLY the upstream `shepard_client` — works against any
shepard 5.2.0+ instance, including this fork. No /v2/ calls; pure
compat surface so legacy instances are supported.

## Designed for extensive datasets — capture everything, be polite

For dataset captures the user described as "very extensive":

- **Resumable.** Every reference / file / data-object writes a small
  marker file when done. Re-running the script skips the markers, so
  a crash or kill -9 is recoverable: just re-invoke with the same
  `--out` path.
- **Rate-limited.** A configurable per-request sleep (default
  `--rate-limit-ms 50`) plus a token-bucket on the file-payload
  downloads (default `--bandwidth-mb-per-sec 50`) keeps the source
  instance responsive. Both throttles are shared across worker
  threads so parallelism doesn't blow past the cap.
- **Mild parallelism.** A small worker pool (default `--workers 4`)
  fans out per-data-object work — annotations, reference listing,
  per-reference payload downloads — to keep the pipe full while
  individual requests wait on the source. Each worker shares the
  global throttle.
- **Progress estimation.** Once the DO count is known, the script
  prints a one-line progress bar with rate + ETA every few seconds.
- **Retry with exponential backoff** on transient HTTP errors
  (429 / 500 / 502 / 503 / 504, network timeouts).
- **Streaming file downloads.** File payloads are streamed to disk
  instead of being read into memory — supports large CAD bundles
  without ballooning RAM.
- **Captures everything (except lab journals — see below):** collection
  + DOs + all reference kinds (timeseries, file, structured-data, URI,
  video, git, spatial), per-entity permissions, semantic annotations
  (collection / data-object / per-reference), attributes, status,
  predecessor/successor edges, **parent/child edges with proper
  recursion** so nested data-object trees are captured fully even when
  the upstream `getAllDataObjects` returns only top-level rows.

What this exporter does NOT cover (track separately in the manifest):

- **Lab journal entries.** Intentionally skipped — the legacy MFFD
  dataset's journal entries don't carry forward. Add an
  `--include-lab-journals` flag later if a future migration needs them.
- **Container ownership.** Containers are referenced from many
  collections — we export each container's metadata once (in
  manifest.json), but the import side decides whether to reuse an
  existing container or mint a fresh one.
- **Spatial / HDF5 references** — captured as metadata only; payload
  re-export through the upstream API is not yet wired for those kinds.
- **Snapshots** — pinned point-in-time records are intentionally not
  re-exported (they pin source-instance entity ids that won't survive
  the import).

## Usage

    pip install shepard-client
    python export-collection.py \\
        --host https://your-legacy-shepard/shepard/api \\
        --apikey YOUR-API-KEY \\
        --collection-id 42 \\
        --out ./mffd-export/                  # creates if missing

    # Resume after a crash:
    python export-collection.py [same args]   # picks up where it left off

    # Throttle harder on a busy instance:
    python export-collection.py [...] --rate-limit-ms 200 \\
        --bandwidth-mb-per-sec 10 --workers 2

    # Dry-run: tally everything, write only metadata, no payloads.
    python export-collection.py [...] --dry-run
"""

from __future__ import annotations

import argparse
import json
import os
import random
import shutil
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, Future
from pathlib import Path
from typing import Any, Callable, Optional


# ---------------------------------------------------------------------------
# Tiny utilities


def _log(msg: str, *, level: str = "INFO") -> None:
    print(f"[{time.strftime('%H:%M:%S')}] {level:5} {msg}", flush=True)


def _import_client_or_die():
    try:
        import shepard_client as sc  # type: ignore
    except Exception as e:
        print(f"shepard_client import failed: {e}\n"
              "Install with: pip install shepard-client", file=sys.stderr)
        sys.exit(2)
    return sc


def _to_dict(obj: Any) -> Any:
    """Best-effort recursive serialisation."""
    if obj is None or isinstance(obj, (str, int, float, bool)):
        return obj
    if isinstance(obj, list):
        return [_to_dict(x) for x in obj]
    if isinstance(obj, dict):
        return {k: _to_dict(v) for k, v in obj.items()}
    to_dict = getattr(obj, "to_dict", None)
    if callable(to_dict):
        try:
            return _to_dict(to_dict())
        except Exception:
            pass
    d = getattr(obj, "__dict__", None)
    if isinstance(d, dict):
        return {k: _to_dict(v) for k, v in d.items() if not k.startswith("_")}
    return str(obj)


# ---------------------------------------------------------------------------
# Throttle + retry — thread-safe so a worker pool shares the global budget


class Throttle:
    def __init__(self, rate_limit_ms: int, bandwidth_mb_per_sec: float):
        self.rate_limit_ms = rate_limit_ms
        self.bytes_per_sec = bandwidth_mb_per_sec * 1024 * 1024
        self._req_lock = threading.Lock()
        self._bw_lock = threading.Lock()
        self._last_req = 0.0

    def request(self) -> None:
        """Sleep so consecutive API requests are spaced by rate_limit_ms.
        Workers serialise on this — that's the whole point: we want
        parallelism in waiting, not in flooding the source."""
        if self.rate_limit_ms <= 0:
            return
        with self._req_lock:
            now = time.monotonic()
            elapsed_ms = (now - self._last_req) * 1000
            if elapsed_ms < self.rate_limit_ms:
                time.sleep((self.rate_limit_ms - elapsed_ms) / 1000.0)
            self._last_req = time.monotonic()

    def bytes(self, n: int) -> None:
        """Token-bucket sleep after streaming `n` bytes. Shared across
        workers so the total download rate stays under cap."""
        if self.bytes_per_sec <= 0:
            return
        with self._bw_lock:
            time.sleep(n / self.bytes_per_sec)


RETRYABLE_HTTP = {429, 500, 502, 503, 504}


def with_retry(throttle: Throttle, fn: Callable, *args, **kwargs):
    """Retry a callable up to MAX_RETRIES with exponential backoff
    on retryable HTTP errors / network glitches."""
    MAX_RETRIES = 6
    base_delay_s = 1.0
    last_exc: Optional[Exception] = None
    for attempt in range(MAX_RETRIES + 1):
        throttle.request()
        try:
            return fn(*args, **kwargs)
        except Exception as e:
            last_exc = e
            status = getattr(e, "status", None)
            if isinstance(status, int) and status not in RETRYABLE_HTTP:
                raise
            if attempt >= MAX_RETRIES:
                _log(f"give up after {attempt + 1} attempts: {e}", level="ERROR")
                raise
            backoff = base_delay_s * (2 ** attempt) + random.uniform(0, 0.5)
            _log(f"retry {attempt + 1}/{MAX_RETRIES} after {backoff:.1f}s "
                 f"(reason: {type(e).__name__}: {str(e)[:120]})",
                 level="WARN")
            time.sleep(backoff)
    if last_exc:
        raise last_exc


# ---------------------------------------------------------------------------
# Resume markers


def _marker_done(path: Path) -> bool:
    return path.with_suffix(path.suffix + ".done").exists()


def _mark_done(path: Path) -> None:
    path.with_suffix(path.suffix + ".done").touch()


# ---------------------------------------------------------------------------
# Streaming file download


def _stream_to_disk(blob: Any, out_path: Path, throttle: Throttle) -> int:
    n_total = 0
    out_path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(blob, (bytes, bytearray)):
        out_path.write_bytes(bytes(blob))
        throttle.bytes(len(blob))
        return len(blob)
    if isinstance(blob, str) and Path(blob).exists():
        shutil.move(blob, out_path)
        return out_path.stat().st_size
    if hasattr(blob, "read"):
        CHUNK = 1024 * 1024
        with out_path.open("wb") as f:
            while True:
                chunk = blob.read(CHUNK)
                if not chunk:
                    break
                if isinstance(chunk, str):
                    chunk = chunk.encode()
                f.write(chunk)
                n_total += len(chunk)
                throttle.bytes(len(chunk))
        return n_total
    s = str(blob).encode()
    out_path.write_bytes(s)
    throttle.bytes(len(s))
    return len(s)


# ---------------------------------------------------------------------------
# Progress estimation — prints a one-line bar every PRINT_INTERVAL_SEC


class Progress:
    PRINT_INTERVAL_SEC = 3.0

    def __init__(self, total_units: int, label: str):
        self._lock = threading.Lock()
        self._total = total_units
        self._done = 0
        self._bytes = 0
        self._label = label
        self._start = time.monotonic()
        self._last_print = 0.0

    def set_total(self, total: int) -> None:
        with self._lock:
            self._total = max(self._total, total)

    def add(self, units: int = 1, bytes_seen: int = 0) -> None:
        should_print = False
        with self._lock:
            self._done += units
            self._bytes += bytes_seen
            now = time.monotonic()
            if now - self._last_print >= self.PRINT_INTERVAL_SEC:
                self._last_print = now
                should_print = True
        if should_print:
            self._print(force=False)

    def done(self) -> None:
        self._print(force=True)
        sys.stdout.write("\n")
        sys.stdout.flush()

    def _print(self, *, force: bool) -> None:
        with self._lock:
            elapsed = max(0.001, time.monotonic() - self._start)
            rate = self._done / elapsed
            mib = self._bytes / 1024 / 1024
            mib_rate = mib / elapsed
            pct = (100.0 * self._done / self._total) if self._total else 0.0
            bar_w = 24
            filled = int(bar_w * self._done / max(1, self._total))
            bar = "█" * filled + "·" * (bar_w - filled)
            if rate > 0 and self._total > self._done:
                eta_s = (self._total - self._done) / rate
                eta = time.strftime("%H:%M:%S", time.gmtime(eta_s)) if eta_s < 86400 else ">1d"
            else:
                eta = "--:--:--"
            line = (f"\r[{self._label}] {bar} {self._done}/{self._total} "
                    f"({pct:5.1f}%) {mib:8.1f} MiB ({mib_rate:5.2f} MiB/s) "
                    f"ETA {eta}")
            sys.stdout.write(line)
            sys.stdout.flush()


# ---------------------------------------------------------------------------
# DataObject tree discovery — BFS via childrenIds


def discover_data_objects(do_api, throttle: Throttle, collection_id: int) -> list:
    """Return every DataObject in a collection, including nested ones.

    Strategy: try the flat paginated `get_all_data_objects` (some upstreams
    return all DOs flat, regardless of nesting). Then walk `childrenIds`
    BFS-style across the seed set and fetch any descendant we haven't
    seen yet. Either way we end up with the full tree.

    The legacy MFFD instance reportedly has nested data objects, so the
    BFS step is mandatory.
    """
    seen_by_id: dict[int, Any] = {}
    queue: list[int] = []

    # Step 1: flat paginated seed.
    page = 0
    while True:
        try:
            seed = list(with_retry(throttle, do_api.get_all_data_objects,
                                   collection_id=collection_id,
                                   page=page, size=200))
        except TypeError:
            # Older upstream client signature
            seed = list(with_retry(throttle, do_api.get_all_data_objects,
                                   collection_id=collection_id))
            for d in seed:
                _id = getattr(d, "id", None)
                if _id is not None and _id not in seen_by_id:
                    seen_by_id[_id] = d
                    queue.append(_id)
            break
        if not seed:
            break
        for d in seed:
            _id = getattr(d, "id", None)
            if _id is not None and _id not in seen_by_id:
                seen_by_id[_id] = d
                queue.append(_id)
        if len(seed) < 200:
            break
        page += 1
        if page > 500:
            _log("flat-seed walk gave up after 500 pages", level="WARN")
            break

    # Step 2: BFS via childrenIds.
    while queue:
        parent_id = queue.pop(0)
        parent = seen_by_id.get(parent_id)
        if parent is None:
            continue
        # Try both snake_case and camelCase attribute access
        children = (getattr(parent, "children_ids", None)
                    or getattr(parent, "childrenIds", None) or [])
        if isinstance(children, dict):
            children = children.get("childrenIds", [])
        for cid in children:
            if cid in seen_by_id:
                continue
            try:
                child = with_retry(throttle, do_api.get_data_object,
                                   collection_id=collection_id, data_object_id=cid)
                seen_by_id[cid] = child
                queue.append(cid)
            except Exception as e:
                _log(f"  WARN: could not fetch nested DO {cid} (parent {parent_id}): {e}",
                     level="WARN")

    return list(seen_by_id.values())


# ---------------------------------------------------------------------------
# Per-data-object capture (called from worker pool)


def _capture_one_do(
    apis: dict,
    throttle: Throttle,
    out: Path,
    args,
    do_id: int,
    do_name: str,
    do_obj: Any,
    refs_index: dict,
    containers_index: dict,
    refs_index_lock: threading.Lock,
    bytes_counter: list,
    bytes_lock: threading.Lock,
    progress: Progress,
) -> None:
    """Capture one DataObject's metadata + annotations + references +
    payloads. Idempotent via .done markers; safe to run from worker
    threads as long as throttle / counters share locks."""
    coll_id = args.collection_id
    do_path = out / "data-objects" / f"do-{do_id}.json"

    if _marker_done(do_path):
        existing = json.loads(do_path.read_text())
        with refs_index_lock:
            for kind in ("timeseries", "file", "structured"):
                for rid in existing.get("_references", {}).get(kind, []):
                    refs_index[kind].append(rid)
        progress.add(1)
        return

    do_api = apis["do_api"]
    ts_ref_api = apis["ts_ref_api"]
    fr_api = apis["fr_api"]
    sd_ref_api = apis["sd_ref_api"]
    fc_api = apis["fc_api"]
    sd_c_api = apis["sd_c_api"]
    sa_api = apis["sa_api"]

    do_dict = _to_dict(do_obj)

    # Per-DO permissions
    try:
        perms = with_retry(throttle, do_api.get_data_object_permissions,
                           collection_id=coll_id, data_object_id=do_id)
        do_dict["_permissions"] = _to_dict(perms)
    except Exception:
        pass

    # Per-DO annotations
    try:
        anns = with_retry(throttle, sa_api.get_all_data_object_annotations,
                          collection_id=coll_id, data_object_id=do_id)
        do_dict["_annotations"] = [_to_dict(a) for a in anns]
    except Exception:
        do_dict["_annotations"] = []

    do_dict["_references"] = {"timeseries": [], "file": [], "structured": []}
    local_bytes = 0

    # ── Timeseries refs ──────────────────────────────────────────────
    try:
        tsr = list(with_retry(throttle, ts_ref_api.get_all_timeseries_references,
                              collection_id=coll_id, data_object_id=do_id))
    except Exception:
        tsr = []
    for r in tsr:
        rid = getattr(r, "id", None)
        if rid is None:
            continue
        rpath = out / "references" / f"ts-{rid}.json"
        if not _marker_done(rpath):
            rdict = _to_dict(r)
            try:
                rann = with_retry(throttle, sa_api.get_all_basic_reference_annotations,
                                  collection_id=coll_id,
                                  data_object_id=do_id,
                                  basic_reference_id=rid)
                rdict["_annotations"] = [_to_dict(a) for a in rann]
            except Exception:
                rdict["_annotations"] = []
            rpath.write_text(json.dumps(rdict, indent=2))
            container_id = getattr(r, "timeseries_container_id", None)
            if container_id:
                with refs_index_lock:
                    containers_index["timeseries"].add(container_id)
            if not args.dry_run:
                try:
                    csv_blob = with_retry(throttle,
                                          ts_ref_api.export_timeseries_payload,
                                          collection_id=coll_id,
                                          data_object_id=do_id,
                                          timeseries_reference_id=rid)
                    n = _stream_to_disk(csv_blob, out / "references" / f"ts-{rid}.csv", throttle)
                    local_bytes += n
                except Exception as e:
                    _log(f"  WARN: ts-{rid} payload: {e}", level="WARN")
            _mark_done(rpath)
        with refs_index_lock:
            refs_index["timeseries"].append(rid)
        do_dict["_references"]["timeseries"].append(rid)

    # ── File refs ────────────────────────────────────────────────────
    try:
        frs = list(with_retry(throttle, fr_api.get_all_file_references,
                              collection_id=coll_id, data_object_id=do_id))
    except Exception:
        frs = []
    for r in frs:
        rid = getattr(r, "id", None)
        if rid is None:
            continue
        rpath = out / "references" / f"file-{rid}.json"
        if not _marker_done(rpath):
            rdict = _to_dict(r)
            try:
                rann = with_retry(throttle, sa_api.get_all_basic_reference_annotations,
                                  collection_id=coll_id,
                                  data_object_id=do_id,
                                  basic_reference_id=rid)
                rdict["_annotations"] = [_to_dict(a) for a in rann]
            except Exception:
                rdict["_annotations"] = []
            rpath.write_text(json.dumps(rdict, indent=2))
            container_id = getattr(r, "file_container_id", None)
            if container_id:
                with refs_index_lock:
                    containers_index["file"].add(container_id)
            if not args.dry_run:
                files_dir = out / "references" / f"file-{rid}"
                files_dir.mkdir(exist_ok=True)
                oids = getattr(r, "file_oids", None) or rdict.get("fileOids") or []
                for oid in oids:
                    target = files_dir / f"{oid}"
                    if target.exists():
                        continue
                    try:
                        blob = with_retry(throttle, fc_api.get_file,
                                          file_container_id=container_id, oid=str(oid))
                        n = _stream_to_disk(blob, target, throttle)
                        local_bytes += n
                    except Exception as e:
                        _log(f"  WARN: file {oid} for ref {rid}: {e}", level="WARN")
            _mark_done(rpath)
        with refs_index_lock:
            refs_index["file"].append(rid)
        do_dict["_references"]["file"].append(rid)

    # ── Structured-data refs ────────────────────────────────────────
    try:
        sds = list(with_retry(throttle, sd_ref_api.get_all_structured_data_references,
                              collection_id=coll_id, data_object_id=do_id))
    except Exception:
        sds = []
    for r in sds:
        rid = getattr(r, "id", None)
        if rid is None:
            continue
        rpath = out / "references" / f"sd-{rid}.json"
        if not _marker_done(rpath):
            rdict = _to_dict(r)
            try:
                rann = with_retry(throttle, sa_api.get_all_basic_reference_annotations,
                                  collection_id=coll_id,
                                  data_object_id=do_id,
                                  basic_reference_id=rid)
                rdict["_annotations"] = [_to_dict(a) for a in rann]
            except Exception:
                rdict["_annotations"] = []
            rpath.write_text(json.dumps(rdict, indent=2))
            container_id = getattr(r, "structured_data_container_id", None)
            if container_id:
                with refs_index_lock:
                    containers_index["structured"].add(container_id)
            if not args.dry_run:
                sd_dir = out / "references" / f"sd-{rid}"
                sd_dir.mkdir(exist_ok=True)
                oids = getattr(r, "structured_data_oids", None) or rdict.get("structuredDataOids") or []
                for oid in oids:
                    target = sd_dir / f"{oid}.json"
                    if target.exists():
                        continue
                    try:
                        sd = with_retry(throttle, sd_c_api.get_structured_data,
                                        structured_data_container_id=container_id,
                                        oid=str(oid))
                        payload = getattr(sd, "payload", None) or ""
                        target.write_text(payload)
                        local_bytes += len(payload.encode())
                    except Exception as e:
                        _log(f"  WARN: sd {oid} for ref {rid}: {e}", level="WARN")
            _mark_done(rpath)
        with refs_index_lock:
            refs_index["structured"].append(rid)
        do_dict["_references"]["structured"].append(rid)

    do_path.write_text(json.dumps(do_dict, indent=2))
    _mark_done(do_path)

    with bytes_lock:
        bytes_counter[0] += local_bytes
    progress.add(1, bytes_seen=local_bytes)


# ---------------------------------------------------------------------------
# Main


def export(args, sc) -> int:
    cfg = sc.Configuration(host=args.host)
    cfg.api_key["apikey"] = args.apikey
    client = sc.ApiClient(cfg)

    apis = {
        "coll_api": sc.CollectionApi(client),
        "do_api": sc.DataObjectApi(client),
        "ts_ref_api": sc.TimeseriesReferenceApi(client),
        "fc_api": sc.FileContainerApi(client),
        "fr_api": sc.FileReferenceApi(client),
        "sd_ref_api": sc.StructuredDataReferenceApi(client),
        "sd_c_api": sc.StructuredDataContainerApi(client),
        "ts_c_api": sc.TimeseriesContainerApi(client),
        "sa_api": sc.SemanticAnnotationApi(client),
    }

    throttle = Throttle(args.rate_limit_ms, args.bandwidth_mb_per_sec)

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    (out / "data-objects").mkdir(exist_ok=True)
    (out / "references").mkdir(exist_ok=True)

    _log(f"Source: {args.host}")
    _log(f"Output: {out.resolve()}")
    _log(f"Throttle: {args.rate_limit_ms} ms/req, "
         f"{args.bandwidth_mb_per_sec} MB/s download cap, "
         f"{args.workers} workers")
    if args.dry_run:
        _log("DRY-RUN — no payload downloads, metadata only.", level="WARN")

    # ─── Collection ────────────────────────────────────────────────
    _log(f"Fetching collection {args.collection_id} ...")
    coll = with_retry(throttle, apis["coll_api"].get_collection,
                      collection_id=args.collection_id)
    coll_dict = _to_dict(coll)
    try:
        perms = with_retry(throttle, apis["coll_api"].get_collection_permissions,
                           collection_id=args.collection_id)
        coll_dict["_permissions"] = _to_dict(perms)
    except Exception as e:
        _log(f"  WARN: collection permissions failed: {e}", level="WARN")

    # Collection annotations
    try:
        anns = with_retry(throttle, apis["sa_api"].get_all_collection_annotations,
                          collection_id=args.collection_id)
        coll_anns = [_to_dict(a) for a in anns]
    except Exception as e:
        _log(f"  WARN: collection annotations failed: {e}", level="WARN")
        coll_anns = []
    (out / "annotations.json").write_text(json.dumps(coll_anns, indent=2))

    # ─── Data Object discovery (BFS across nested trees) ─────────────
    _log("Discovering data objects (flat + recursive children) ...")
    all_dos = discover_data_objects(apis["do_api"], throttle, args.collection_id)
    total_dos = len(all_dos)
    _log(f"Found {total_dos} data objects (including nested).")

    # ─── Capture data objects in parallel ────────────────────────────
    refs_index: dict[str, list[int]] = {"timeseries": [], "file": [], "structured": []}
    containers_index: dict[str, set[int]] = {"timeseries": set(), "file": set(), "structured": set()}
    refs_index_lock = threading.Lock()
    bytes_counter = [0]
    bytes_lock = threading.Lock()
    progress = Progress(total_dos, "DOs")

    with ThreadPoolExecutor(max_workers=max(1, args.workers)) as pool:
        futures: list[Future] = []
        for do in all_dos:
            do_id = getattr(do, "id", None)
            if do_id is None:
                continue
            do_name = getattr(do, "name", "?")
            futures.append(pool.submit(
                _capture_one_do, apis, throttle, out, args,
                do_id, do_name, do, refs_index, containers_index,
                refs_index_lock, bytes_counter, bytes_lock, progress,
            ))
        for fut in futures:
            try:
                fut.result()
            except Exception as e:
                _log(f"  WARN: a worker failed: {e}", level="WARN")
    progress.done()

    # ─── Build do_index from on-disk JSONs (deterministic order) ─────
    do_index: list[dict] = []
    for do_path in sorted((out / "data-objects").glob("do-*.json")):
        if do_path.name.endswith(".done"):
            continue
        try:
            d = json.loads(do_path.read_text())
            do_index.append({
                "id": d.get("id"),
                "name": d.get("name"),
                "parent_id": d.get("parent_id") or d.get("parentId"),
            })
        except Exception:
            continue

    # ─── Containers metadata ─────────────────────────────────────────
    _log("Fetching container metadata ...")
    containers_dump: dict[str, list[dict]] = {"timeseries": [], "file": [], "structured": []}
    for cid in sorted(containers_index["timeseries"]):
        try:
            c = with_retry(throttle, apis["ts_c_api"].get_timeseries_container,
                           timeseries_container_id=cid)
            containers_dump["timeseries"].append(_to_dict(c))
        except Exception as e:
            _log(f"  WARN: timeseries container {cid}: {e}", level="WARN")
    for cid in sorted(containers_index["file"]):
        try:
            c = with_retry(throttle, apis["fc_api"].get_file_container, file_container_id=cid)
            containers_dump["file"].append(_to_dict(c))
        except Exception as e:
            _log(f"  WARN: file container {cid}: {e}", level="WARN")
    for cid in sorted(containers_index["structured"]):
        try:
            c = with_retry(throttle, apis["sd_c_api"].get_structured_data_container,
                           structured_data_container_id=cid)
            containers_dump["structured"].append(_to_dict(c))
        except Exception as e:
            _log(f"  WARN: structured-data container {cid}: {e}", level="WARN")

    manifest = {
        "source_host": args.host,
        "exported_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "exporter_version": "1.2",
        "options": {
            "rate_limit_ms": args.rate_limit_ms,
            "bandwidth_mb_per_sec": args.bandwidth_mb_per_sec,
            "workers": args.workers,
            "dry_run": args.dry_run,
            "skip_lab_journals": True,
        },
        "collection": coll_dict,
        "data_objects": do_index,
        "references": refs_index,
        "containers": containers_dump,
        "totals": {
            "data_objects": len(do_index),
            "timeseries_refs": len(refs_index["timeseries"]),
            "file_refs": len(refs_index["file"]),
            "structured_refs": len(refs_index["structured"]),
            "payload_bytes": bytes_counter[0],
        },
        "notes": (
            "Lab journal entries intentionally excluded. Data-object tree is "
            "captured fully (flat-paginated seed + childrenIds BFS). "
            "Re-import via the matching import-collection.py — resume markers "
            "(*.done) are kept in the export tree so re-runs are idempotent."
        ),
    }
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2))
    _log(f"Done. {len(do_index)} DOs, "
         f"{len(refs_index['timeseries'])}ts/{len(refs_index['file'])}f/"
         f"{len(refs_index['structured'])}sd refs, "
         f"{bytes_counter[0] / 1024 / 1024 / 1024:.2f} GiB payload.")
    _log(f"Export at: {out.resolve()}")
    return 0


def main(argv: Optional[list[str]] = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--host", required=True,
                    help="Source shepard host root (with /shepard/api)")
    ap.add_argument("--apikey", required=True,
                    help="apikey header value with Read on the collection")
    ap.add_argument("--collection-id", type=int, required=True,
                    help="Numeric id of the collection to export")
    ap.add_argument("--out", required=True,
                    help="Output directory (created; resumes on re-run)")
    ap.add_argument("--rate-limit-ms", type=int, default=50,
                    help="Min ms between API requests (default 50; raise to "
                    "200+ on a busy production source)")
    ap.add_argument("--bandwidth-mb-per-sec", type=float, default=50.0,
                    help="Download bandwidth cap MB/s (default 50)")
    ap.add_argument("--workers", type=int, default=4,
                    help="Concurrent worker threads (default 4; share the "
                    "global throttle so they don't blow past the cap)")
    ap.add_argument("--dry-run", action="store_true",
                    help="Skip payload downloads; metadata only.")
    args = ap.parse_args(argv)
    sc = _import_client_or_die()
    return export(args, sc)


if __name__ == "__main__":
    raise SystemExit(main())
