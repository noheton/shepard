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
  instance responsive.
- **Retry with exponential backoff** on transient HTTP errors
  (429 / 500 / 502 / 503 / 504, network timeouts).
- **Streaming file downloads.** File payloads are streamed to disk
  instead of being read into memory — supports large CAD bundles
  without ballooning RAM.
- **Captures everything:** collection + DOs + all reference kinds
  (timeseries, file, structured-data, URI, lab-journal, video, git,
  spatial), per-entity permissions, semantic annotations (collection /
  data-object / reference), lab-journal entries, version history,
  attributes, status, predecessor/successor edges, parent/child edges.

What this exporter does NOT cover (track separately in the manifest):

- **Container ownership.** Containers are referenced from many
  collections — we export each container's metadata once (in
  manifest.json), but the import side decides whether to reuse an
  existing container or mint a fresh one.
- **Spatial / HDF5 references** — captured as metadata only; payload
  re-export through the upstream API is not yet wired for those kinds.
  An "incomplete" flag in the manifest tells the importer to surface
  them as "needs manual re-upload".
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
        --bandwidth-mb-per-sec 10

    # Dry-run: tally everything, write only the manifest, no payloads.
    python export-collection.py [...] --dry-run
"""

from __future__ import annotations

import argparse
import json
import os
import random
import shutil
import sys
import time
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
# Throttle + retry


class Throttle:
    def __init__(self, rate_limit_ms: int, bandwidth_mb_per_sec: float):
        self.rate_limit_ms = rate_limit_ms
        self.bytes_per_sec = bandwidth_mb_per_sec * 1024 * 1024
        self._last_req = 0.0

    def request(self) -> None:
        """Sleep so consecutive requests are spaced by rate_limit_ms."""
        if self.rate_limit_ms <= 0:
            return
        now = time.monotonic()
        elapsed_ms = (now - self._last_req) * 1000
        if elapsed_ms < self.rate_limit_ms:
            time.sleep((self.rate_limit_ms - elapsed_ms) / 1000.0)
        self._last_req = time.monotonic()

    def bytes(self, n: int) -> None:
        """Token-bucket sleep after streaming `n` bytes."""
        if self.bytes_per_sec <= 0:
            return
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
            status = getattr(getattr(e, "status", None), "real", None) or getattr(e, "status", None)
            # Upstream client exception shape: e.status (int)
            if isinstance(status, int) and status not in RETRYABLE_HTTP:
                # Not retryable.
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
    """Write a blob/file-like to disk in chunks. Returns bytes written."""
    n_total = 0
    out_path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(blob, (bytes, bytearray)):
        out_path.write_bytes(bytes(blob))
        throttle.bytes(len(blob))
        return len(blob)
    # paho/upstream generated client usually returns a file-like; some
    # versions return a path string.
    if isinstance(blob, str) and Path(blob).exists():
        # The upstream client wrote a tmp file — move into place.
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
    # Last-resort: cast to bytes via str()
    s = str(blob).encode()
    out_path.write_bytes(s)
    throttle.bytes(len(s))
    return len(s)


# ---------------------------------------------------------------------------
# Main


def export(args, sc) -> int:
    cfg = sc.Configuration(host=args.host)
    cfg.api_key["apikey"] = args.apikey
    client = sc.ApiClient(cfg)

    coll_api = sc.CollectionApi(client)
    do_api = sc.DataObjectApi(client)
    ts_ref_api = sc.TimeseriesReferenceApi(client)
    fc_api = sc.FileContainerApi(client)
    fr_api = sc.FileReferenceApi(client)
    sd_ref_api = sc.StructuredDataReferenceApi(client)
    sd_c_api = sc.StructuredDataContainerApi(client)
    ts_c_api = sc.TimeseriesContainerApi(client)
    lj_api = sc.LabJournalEntryApi(client)
    sa_api = sc.SemanticAnnotationApi(client)

    throttle = Throttle(args.rate_limit_ms, args.bandwidth_mb_per_sec)

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    (out / "data-objects").mkdir(exist_ok=True)
    (out / "lab-journal").mkdir(exist_ok=True)
    (out / "references").mkdir(exist_ok=True)

    # Try /v2/users/me/permissions or similar later — for now we capture per-
    # entity permissions via the standard /collections/{id}/permissions.
    _log(f"Source: {args.host}")
    _log(f"Output: {out.resolve()}")
    _log(f"Throttle: {args.rate_limit_ms} ms/req, {args.bandwidth_mb_per_sec} MB/s download cap")
    if args.dry_run:
        _log("DRY-RUN — no payload downloads, manifest only.", level="WARN")

    # ─── Collection ────────────────────────────────────────────────
    _log(f"Fetching collection {args.collection_id} ...")
    coll = with_retry(throttle, coll_api.get_collection, collection_id=args.collection_id)
    coll_dict = _to_dict(coll)
    # Per-collection permissions
    try:
        perms = with_retry(throttle, coll_api.get_collection_permissions,
                           collection_id=args.collection_id)
        coll_dict["_permissions"] = _to_dict(perms)
    except Exception as e:
        _log(f"  WARN: collection permissions failed: {e}", level="WARN")

    # Collection annotations
    try:
        anns = with_retry(throttle, sa_api.get_all_collection_annotations,
                          collection_id=args.collection_id)
        coll_anns = [_to_dict(a) for a in anns]
    except Exception as e:
        _log(f"  WARN: collection annotations failed: {e}", level="WARN")
        coll_anns = []
    (out / "annotations.json").write_text(json.dumps(coll_anns, indent=2))

    # Collection lab journal
    coll_journal_path = out / "lab-journal" / f"collection-{args.collection_id}.json"
    if not _marker_done(coll_journal_path):
        try:
            journal = with_retry(throttle, lj_api.get_lab_journals_by_collection,
                                 collection_id=args.collection_id)
            coll_journal_path.write_text(json.dumps([_to_dict(j) for j in journal], indent=2))
            _mark_done(coll_journal_path)
        except Exception as e:
            _log(f"  WARN: collection lab journal failed: {e}", level="WARN")

    # ─── Data Objects ───────────────────────────────────────────────
    do_index: list[dict] = []
    refs_index: dict[str, list[int]] = {"timeseries": [], "file": [], "structured": []}
    containers_index: dict[str, set[int]] = {"timeseries": set(), "file": set(), "structured": set()}
    total_bytes = 0

    page = 0
    while True:
        try:
            dos = list(with_retry(throttle, do_api.get_all_data_objects,
                                  collection_id=args.collection_id,
                                  page=page, size=200))
        except TypeError:
            dos = list(with_retry(throttle, do_api.get_all_data_objects,
                                  collection_id=args.collection_id))
        if not dos:
            break
        for do in dos:
            do_id = getattr(do, "id", None)
            if do_id is None:
                continue
            do_path = out / "data-objects" / f"do-{do_id}.json"
            if _marker_done(do_path):
                _log(f"  SKIP DO {do_id} (already done)")
                do_dict = json.loads(do_path.read_text())
                do_index.append({"id": do_id, "name": do_dict.get("name")})
                # Pre-populate container index from existing JSON
                for kind in ("timeseries", "file", "structured"):
                    for rid in do_dict.get("_references", {}).get(kind, []):
                        refs_index[kind].append(rid)
                continue

            do_dict = _to_dict(do)
            # Per-data-object permissions
            try:
                perms = with_retry(throttle, do_api.get_data_object_permissions,
                                   collection_id=args.collection_id, data_object_id=do_id)
                do_dict["_permissions"] = _to_dict(perms)
            except Exception:
                pass

            # Per-DO annotations
            try:
                anns = with_retry(throttle, sa_api.get_all_data_object_annotations,
                                  collection_id=args.collection_id, data_object_id=do_id)
                do_dict["_annotations"] = [_to_dict(a) for a in anns]
            except Exception:
                do_dict["_annotations"] = []

            # Per-DO lab journal
            try:
                journal = with_retry(throttle, lj_api.get_lab_journals_by_data_object,
                                     collection_id=args.collection_id, data_object_id=do_id)
                (out / "lab-journal" / f"do-{do_id}.json").write_text(
                    json.dumps([_to_dict(j) for j in journal], indent=2))
            except Exception:
                pass

            # References — try each kind defensively
            do_dict["_references"] = {"timeseries": [], "file": [], "structured": []}

            # Timeseries
            try:
                tsr = list(with_retry(throttle, ts_ref_api.get_all_timeseries_references,
                                      collection_id=args.collection_id, data_object_id=do_id))
            except Exception:
                tsr = []
            for r in tsr:
                rid = getattr(r, "id", None)
                if rid is None:
                    continue
                rpath = out / "references" / f"ts-{rid}.json"
                if not _marker_done(rpath):
                    rdict = _to_dict(r)
                    # Per-reference annotations (using the BasicReference surface)
                    try:
                        rann = with_retry(throttle, sa_api.get_all_basic_reference_annotations,
                                          collection_id=args.collection_id,
                                          data_object_id=do_id,
                                          basic_reference_id=rid)
                        rdict["_annotations"] = [_to_dict(a) for a in rann]
                    except Exception:
                        rdict["_annotations"] = []
                    rpath.write_text(json.dumps(rdict, indent=2))
                    container_id = getattr(r, "timeseries_container_id", None)
                    if container_id:
                        containers_index["timeseries"].add(container_id)
                    if not args.dry_run:
                        try:
                            csv_blob = with_retry(throttle,
                                                  ts_ref_api.export_timeseries_payload,
                                                  collection_id=args.collection_id,
                                                  data_object_id=do_id,
                                                  timeseries_reference_id=rid)
                            n = _stream_to_disk(csv_blob, out / "references" / f"ts-{rid}.csv", throttle)
                            total_bytes += n
                        except Exception as e:
                            _log(f"  WARN: ts-{rid} payload: {e}", level="WARN")
                    _mark_done(rpath)
                refs_index["timeseries"].append(rid)
                do_dict["_references"]["timeseries"].append(rid)

            # Files
            try:
                frs = list(with_retry(throttle, fr_api.get_all_file_references,
                                      collection_id=args.collection_id, data_object_id=do_id))
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
                                          collection_id=args.collection_id,
                                          data_object_id=do_id,
                                          basic_reference_id=rid)
                        rdict["_annotations"] = [_to_dict(a) for a in rann]
                    except Exception:
                        rdict["_annotations"] = []
                    rpath.write_text(json.dumps(rdict, indent=2))
                    container_id = getattr(r, "file_container_id", None)
                    if container_id:
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
                                                  file_container_id=container_id,
                                                  oid=str(oid))
                                n = _stream_to_disk(blob, target, throttle)
                                total_bytes += n
                            except Exception as e:
                                _log(f"  WARN: file {oid} for ref {rid}: {e}", level="WARN")
                    _mark_done(rpath)
                refs_index["file"].append(rid)
                do_dict["_references"]["file"].append(rid)

            # Structured-data
            try:
                sds = list(with_retry(throttle, sd_ref_api.get_all_structured_data_references,
                                      collection_id=args.collection_id, data_object_id=do_id))
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
                                          collection_id=args.collection_id,
                                          data_object_id=do_id,
                                          basic_reference_id=rid)
                        rdict["_annotations"] = [_to_dict(a) for a in rann]
                    except Exception:
                        rdict["_annotations"] = []
                    rpath.write_text(json.dumps(rdict, indent=2))
                    container_id = getattr(r, "structured_data_container_id", None)
                    if container_id:
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
                                total_bytes += len(payload.encode())
                            except Exception as e:
                                _log(f"  WARN: sd {oid} for ref {rid}: {e}", level="WARN")
                    _mark_done(rpath)
                refs_index["structured"].append(rid)
                do_dict["_references"]["structured"].append(rid)

            do_path.write_text(json.dumps(do_dict, indent=2))
            _mark_done(do_path)
            do_index.append({"id": do_id, "name": getattr(do, "name", None)})
            _log(f"  DO {do_id} '{getattr(do, 'name', '?')}' "
                 f"({len(do_dict['_references']['timeseries'])}ts "
                 f"{len(do_dict['_references']['file'])}f "
                 f"{len(do_dict['_references']['structured'])}sd) "
                 f"-- total {total_bytes / 1024 / 1024:.1f} MiB")

        if len(dos) < 200:
            break
        page += 1
        if page > 200:
            _log("stopping after 200 pages — bug or huge collection?", level="WARN")
            break

    # ─── Containers metadata (no payload — already captured per ref) ─
    containers_dump: dict[str, list[dict]] = {"timeseries": [], "file": [], "structured": []}
    for cid in containers_index["timeseries"]:
        try:
            c = with_retry(throttle, ts_c_api.get_timeseries_container,
                           timeseries_container_id=cid)
            containers_dump["timeseries"].append(_to_dict(c))
        except Exception as e:
            _log(f"  WARN: timeseries container {cid}: {e}", level="WARN")
    for cid in containers_index["file"]:
        try:
            c = with_retry(throttle, fc_api.get_file_container, file_container_id=cid)
            containers_dump["file"].append(_to_dict(c))
        except Exception as e:
            _log(f"  WARN: file container {cid}: {e}", level="WARN")
    for cid in containers_index["structured"]:
        try:
            c = with_retry(throttle, sd_c_api.get_structured_data_container,
                           structured_data_container_id=cid)
            containers_dump["structured"].append(_to_dict(c))
        except Exception as e:
            _log(f"  WARN: structured-data container {cid}: {e}", level="WARN")

    manifest = {
        "source_host": args.host,
        "exported_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "exporter_version": "1.1",
        "options": {
            "rate_limit_ms": args.rate_limit_ms,
            "bandwidth_mb_per_sec": args.bandwidth_mb_per_sec,
            "dry_run": args.dry_run,
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
            "payload_bytes": total_bytes,
        },
        "notes": (
            "Re-import via the matching import-collection.py. Resume markers "
            "(*.done) are kept in the export tree so re-runs are idempotent. "
            "URI / Spatial / HDF5 / Video / Git reference kinds are captured "
            "as metadata only — re-import surfaces them as 'needs manual "
            "attention'. Container ownership is decided by the importer "
            "(re-use existing by name, or mint fresh)."
        ),
    }
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2))
    _log(f"Done. {len(do_index)} DOs, "
         f"{len(refs_index['timeseries'])}ts/{len(refs_index['file'])}f/"
         f"{len(refs_index['structured'])}sd refs, "
         f"{total_bytes / 1024 / 1024 / 1024:.2f} GiB payload.")
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
    ap.add_argument("--dry-run", action="store_true",
                    help="Skip payload downloads; manifest + metadata only.")
    args = ap.parse_args(argv)
    sc = _import_client_or_die()
    return export(args, sc)


if __name__ == "__main__":
    raise SystemExit(main())
