#!/usr/bin/env python3
"""
mffd-spotwelding-import.py — import the MFFD "spot welding" (W8a) wave into a
freshly-reset live Shepard instance, from LOCAL data only.

This is the wave after ``mffd-bridge-replay.py`` (bridge welding). It reuses the
proven idempotency discipline from that script (``Client`` with retry-forever
backoff, a ``LiveIndex`` built at startup for reuse-before-create keyed on a
stable source-id annotation, skip-if-present with a confirming re-read to defeat
the by-data-object projection's consistency lag, sqlite resumable state, and
graph-based verification). The shared helpers are imported from the bridge
script where practical; the bridge script itself is NOT modified.

Source (LOCAL, read-only)
-------------------------
``/mnt/pve/unas/dump/dataset/Punktschweißungen/*.svdx`` — 21 Beckhoff TwinCAT
Scope project files (ultrasonic spot welding). Bytes are read only from local
disk; nothing is ever fetched from a remote backend.

Target
------
- Collection ``mffd-spot-welding`` appId
  ``019ed455-67f7-7725-bf2d-7cd1b67aca9f``.
- Host ``https://shepard-api.nuclide.systems`` (override ``--host``).
- Auth header ``X-API-KEY: <jwt>`` (``--api-key`` / ``SHEPARD_API_KEY`` env / the
  key file). The key carries ``instance-admin``.

How svdx ingestion works in the CURRENT codebase (mechanism a)
--------------------------------------------------------------
There is NO dedicated svdx ingest/parse REST endpoint and NO
``shepard-importer --source svdx`` CLI — the old ``POST /v2/svdx/ingest``
namespace was dissolved (V2CONV-A7-SVDX-REST-DISSOLVE) onto the generic
``TransformExecutor`` SPI. Uploading a ``.svdx`` as a singleton FileReference
(multipart ``POST /v2/files?parentDataObjectAppId=…&name=…``) automatically
triggers the ``fileformat-svdx`` ``FileParserPlugin`` server-side:
``SingletonFileReferenceService`` calls ``FileParserRegistry.anyAccepts`` then
``runAll``, which runs ``SvdxManifestParser.parse`` and writes
``urn:shepard:svdx:*`` semantic annotations onto the new FileReference's appId
(format version, project/data-pool GUIDs, channel/acquisition counts, AMS NetIds,
ports, data types, channel names, symbol names). It does NOT create timeseries on
upload — CSV→TimescaleDB ingest is a separate explicit MAPPING_RECIPE step, out
of scope for this wave (we import the 21 ``.svdx`` files only).

So the importer just: one DataObject per ``.svdx`` (name = original filename),
upload the ``.svdx`` as a singleton FileReference (FR1b), and let the server-side
parser produce the annotations. We additionally write two provenance annotations
on each DataObject:
  - ``urn:shepard:source:provenance = Punktschweißungen/<basename>``
  - ``urn:shepard:source:svdx-file  = <basename>``  (stable cross-run key)

Idempotency
-----------
The ``svdx-file`` basename is the stable source id. A ``LiveIndex`` built at
startup maps that literal → DO appId and (by name) DO appId, plus each live DO's
existing file-reference labels. Reuse-before-create: a DO present in the index is
reused, never re-created; a file ref already attached (confirmed by a fresh
re-read) is skipped. Re-runs converge to exactly 21 DOs + 21 file refs and create
ZERO new graph nodes.

Usage
-----
    python3 mffd-spotwelding-import.py --dry-run
    python3 mffd-spotwelding-import.py --limit 3 --verbose
    python3 mffd-spotwelding-import.py            # full run, all 21 files
"""
from __future__ import annotations

import argparse
import os
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
import sqlite3
import threading
from typing import Optional

try:
    import requests
except ImportError:
    sys.stderr.write("ERROR: this script needs `requests`.  pip install requests\n")
    sys.exit(2)

# Reuse the proven Client (retry-forever backoff) + LiveIndex + key loader from
# the bridge replay script, which lives alongside this one. We do NOT modify it.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from importlib import import_module

_bridge = import_module("mffd-bridge-replay")
Client = _bridge.Client  # type: ignore[attr-defined]
load_api_key = _bridge.load_api_key  # type: ignore[attr-defined]

# ── defaults ──────────────────────────────────────────────────────────────────
DEFAULT_HOST = "https://shepard-api.nuclide.systems"
DEFAULT_SOURCE_DIR = "/mnt/pve/unas/dump/dataset/Punktschweißungen"
DEFAULT_KEYFILE = "/root/.claude/uploads/mffd-import-key-2026-06-17.txt"
TARGET_COLLECTION_APPID = "019ed455-67f7-7725-bf2d-7cd1b67aca9f"
DATASET_ROOT_MARKER = "Punktschweißungen"  # provenance paths are dataset-relative
PROVENANCE_PREDICATE = "urn:shepard:source:provenance"
# Stable cross-run source id. These are files (not cube3 DOs), so the filename
# basename is the natural stable key — the spot-welding analogue of the bridge
# wave's urn:shepard:source:cube3-do-id.
SOURCE_SVDX_FILE_PREDICATE = "urn:shepard:source:svdx-file"
DEFAULT_STATE = os.path.expanduser("~/.mffd-spotwelding-import-state.db")


# ── small thread-safe sqlite state store ───────────────────────────────────────
class State:
    """Resumable progress, keyed by the .svdx basename.

      dos(basename TEXT PK, do_appid TEXT, annotated INTEGER)
      file_refs(basename TEXT PK, ref_appid TEXT, uploaded INTEGER, annotated INTEGER)
    """

    def __init__(self, path: str):
        self._lock = threading.Lock()
        self._conn = sqlite3.connect(path, check_same_thread=False)
        self._conn.execute("PRAGMA journal_mode=WAL")
        self._conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS dos(
              basename TEXT PRIMARY KEY, do_appid TEXT, annotated INTEGER DEFAULT 0);
            CREATE TABLE IF NOT EXISTS file_refs(
              basename TEXT PRIMARY KEY, ref_appid TEXT,
              uploaded INTEGER DEFAULT 0, annotated INTEGER DEFAULT 0);
            """
        )
        self._conn.commit()

    def get_do(self, basename: str):
        with self._lock:
            return self._conn.execute(
                "SELECT do_appid, annotated FROM dos WHERE basename=?", (basename,)
            ).fetchone()

    def put_do(self, basename: str, appid: str):
        with self._lock:
            self._conn.execute(
                "INSERT INTO dos(basename,do_appid) VALUES(?,?) "
                "ON CONFLICT(basename) DO UPDATE SET do_appid=excluded.do_appid",
                (basename, appid),
            )
            self._conn.commit()

    def mark_do_annotated(self, basename: str):
        with self._lock:
            self._conn.execute("UPDATE dos SET annotated=1 WHERE basename=?", (basename,))
            self._conn.commit()

    def get_file(self, basename: str):
        with self._lock:
            return self._conn.execute(
                "SELECT ref_appid, uploaded, annotated FROM file_refs WHERE basename=?",
                (basename,),
            ).fetchone()

    def put_file(self, basename: str, ref_appid: str, uploaded: int = 0):
        with self._lock:
            self._conn.execute(
                "INSERT INTO file_refs(basename,ref_appid,uploaded) VALUES(?,?,?) "
                "ON CONFLICT(basename) DO UPDATE SET ref_appid=excluded.ref_appid, "
                "uploaded=MAX(file_refs.uploaded, excluded.uploaded)",
                (basename, ref_appid, uploaded),
            )
            self._conn.commit()

    def mark_file_annotated(self, basename: str):
        with self._lock:
            self._conn.execute(
                "UPDATE file_refs SET annotated=1 WHERE basename=?", (basename,))
            self._conn.commit()


# ── live-Shepard index (idempotency against LIVE state) ─────────────────────────
class LiveIndex:
    """Snapshot of what already exists in the target collection, built once at
    startup. Maps the stable svdx-file source id → DO appId, and DO name → appId,
    and preloads each live DO's file-reference labels from a settled snapshot."""

    def __init__(self, client: Client, collection_appid: str, workers: int = 6):
        self.c = client
        self.col = collection_appid
        self.workers = workers
        self.by_source_id: dict[str, str] = {}
        self.by_name: dict[str, str] = {}
        self.file_refs_by_do: dict[str, set] = {}

    def build(self, verbose: bool = False):
        page = 0
        n = 0
        do_appids: list[str] = []
        while True:
            batch = self.c.get_json(
                f"/v2/collections/{self.col}/data-objects?page={page}&pageSize=500"
            )
            if not batch:
                break
            for d in batch:
                self.by_name[d["name"]] = d["appId"]
                do_appids.append(d["appId"])
            n += len(batch)
            page += 1

        def _fetch(appid):
            try:
                lst = self.c.get_json(f"/v2/files/by-data-object/{appid}")
                return appid, {x.get("name") for x in lst if isinstance(lst, list) and x.get("name")}
            except RuntimeError:
                return appid, set()

        if do_appids:
            with ThreadPoolExecutor(max_workers=self.workers) as ex:
                for appid, names in ex.map(_fetch, do_appids):
                    self.file_refs_by_do[appid] = names

        page = 0
        anns = 0
        while True:
            batch = self.c.get_json(
                f"/v2/annotations?predicateIri={requests.utils.quote(SOURCE_SVDX_FILE_PREDICATE)}"
                f"&page={page}&pageSize=200"
            )
            if not batch:
                break
            for a in batch:
                lit = a.get("objectLiteral")
                subj = a.get("subjectAppId")
                if lit and subj:
                    self.by_source_id[lit] = subj
            anns += len(batch)
            page += 1
        if verbose:
            print(f"live index    : {n} existing DOs, {anns} source-id annotations",
                  file=sys.stderr, flush=True)

    def find_do(self, basename: str, do_name: str) -> Optional[str]:
        hit = self.by_source_id.get(basename)
        if hit:
            return hit
        return self.by_name.get(do_name)


# ── counts ──────────────────────────────────────────────────────────────────────
@dataclass
class Counts:
    dos_created: int = 0
    dos_reused: int = 0
    file_refs: int = 0
    file_refs_reused: int = 0
    bytes_uploaded: int = 0
    annotations: int = 0
    annotation_failures: int = 0
    _lock: threading.Lock = field(default_factory=threading.Lock, repr=False)

    def add(self, **kw):
        with self._lock:
            for k, v in kw.items():
                setattr(self, k, getattr(self, k) + v)


# ── importer ────────────────────────────────────────────────────────────────────
class Importer:
    def __init__(self, client: Client, state: State, counts: Counts,
                 collection_appid: str, dry_run: bool, verbose: bool,
                 live: Optional[LiveIndex] = None):
        self.c = client
        self.st = state
        self.n = counts
        self.col = collection_appid
        self.dry = dry_run
        self.verbose = verbose
        self.live = live
        self._lock = threading.Lock()
        self._live_file_refs: dict[str, set[str]] = (
            {k: set(v) for k, v in live.file_refs_by_do.items()} if live else {}
        )

    def _existing_file_ref_names(self, do_appid: str, refresh: bool = False) -> set:
        if not refresh:
            with self._lock:
                cached = self._live_file_refs.get(do_appid)
            if cached is not None:
                return cached
        names: set = set()
        try:
            lst = self.c.get_json(f"/v2/files/by-data-object/{do_appid}")
            if isinstance(lst, list):
                names = {x.get("name") for x in lst if x.get("name")}
        except RuntimeError as e:
            if self.verbose:
                print(f"  [live-refs WARN] file list {do_appid}: {e}",
                      file=sys.stderr, flush=True)
        with self._lock:
            merged = self._live_file_refs.get(do_appid, set()) | names
            self._live_file_refs[do_appid] = merged
            return merged

    def _note_live_file(self, do_appid: str, name: str):
        with self._lock:
            self._live_file_refs.setdefault(do_appid, set()).add(name)

    def _annotate(self, subject_appid: str, subject_kind: str, value: str,
                  predicate: str = PROVENANCE_PREDICATE) -> bool:
        if self.dry:
            return True
        body = {
            "subjectAppId": subject_appid,
            "subjectKind": subject_kind,
            "predicateIri": predicate,
            "objectLiteral": value,
            "sourceMode": "ai",
        }
        try:
            self.c.post_json("/v2/annotations", body)
            self.n.add(annotations=1)
            return True
        except RuntimeError as e:
            self.n.add(annotation_failures=1)
            if self.verbose:
                print(f"  [annotation WARN] {subject_kind} {subject_appid}: {e}",
                      file=sys.stderr, flush=True)
            return False

    def import_file(self, abspath: str):
        basename = os.path.basename(abspath)
        size = os.path.getsize(abspath)

        if self.dry:
            self.n.add(dos_created=1, file_refs=1, bytes_uploaded=size)
            return

        # 1. DataObject — idempotent against sqlite, then live index, then create.
        cached = self.st.get_do(basename)
        annotated = 0
        do_appid = None
        if cached and cached[0]:
            do_appid, annotated = cached
            self.n.add(dos_reused=1)
        else:
            live_appid = self.live.find_do(basename, basename) if self.live else None
            if live_appid:
                do_appid = live_appid
                self.st.put_do(basename, do_appid)
                self.n.add(dos_reused=1)
            else:
                created = self.c.post_json(
                    f"/v2/collections/{self.col}/data-objects",
                    {"name": basename},
                )
                do_appid = created["appId"]
                self.st.put_do(basename, do_appid)
                self.n.add(dos_created=1)
                if self.live:
                    self.live.by_name[basename] = do_appid

        # 2. provenance + stable source-id annotations on the DataObject.
        # Guarded by the live source-id index: a DO that already carries its
        # svdx-file annotation has been fully annotated, so a re-run writes ZERO.
        already_annotated = self.live and basename in self.live.by_source_id
        if not annotated and not already_annotated:
            self._annotate(do_appid, "DataObject", f"{DATASET_ROOT_MARKER}/{basename}")
            self._annotate(do_appid, "DataObject", basename,
                           predicate=SOURCE_SVDX_FILE_PREDICATE)
            if self.live:
                self.live.by_source_id[basename] = do_appid
            self.st.mark_do_annotated(basename)

        # 3. singleton FileReference (FR1b). The server-side fileformat-svdx
        # parser fires automatically and emits urn:shepard:svdx:* annotations.
        fcached = self.st.get_file(basename)
        ref_appid = None
        uploaded = fann = 0
        if fcached:
            ref_appid, uploaded, fann = fcached

        ref_label = basename
        if not fcached:
            if ref_label in self._existing_file_ref_names(do_appid):
                self.n.add(file_refs=1, file_refs_reused=1)
                return
            if ref_label in self._existing_file_ref_names(do_appid, refresh=True):
                self.n.add(file_refs=1, file_refs_reused=1)
                return

        if not ref_appid or not uploaded:
            with open(abspath, "rb") as fh:
                resp = self.c.request(
                    "POST",
                    f"/v2/files?name={requests.utils.quote(ref_label)}"
                    f"&parentDataObjectAppId={do_appid}",
                    files={"file": (basename, fh, "application/octet-stream")},
                    expect=(200, 201),
                    retry_on_403=True,  # tolerate :Permissions wiring lag post-DO-create
                )
            created = resp.json()
            ref_appid = created["appId"]
            self.st.put_file(basename, ref_appid, uploaded=1)
            self._note_live_file(do_appid, ref_label)
            self.n.add(bytes_uploaded=size)

        self.n.add(file_refs=1)

        if not fann:
            self._annotate(ref_appid, "Reference", f"{DATASET_ROOT_MARKER}/{basename}")
            self.st.mark_file_annotated(basename)


# ── orchestration ───────────────────────────────────────────────────────────────
def discover_svdx(source_dir: str) -> list[str]:
    if not os.path.isdir(source_dir):
        sys.stderr.write(f"ERROR: source dir not found: {source_dir}\n")
        sys.exit(1)
    files = sorted(
        os.path.join(source_dir, f)
        for f in os.listdir(source_dir)
        if f.lower().endswith(".svdx")
    )
    return files


def main():
    ap = argparse.ArgumentParser(description="Import the MFFD spot-welding (.svdx) wave into Shepard.")
    ap.add_argument("--source-dir", default=DEFAULT_SOURCE_DIR)
    ap.add_argument("--host", default=DEFAULT_HOST)
    ap.add_argument("--api-key", default=None)
    ap.add_argument("--keyfile", default=DEFAULT_KEYFILE)
    ap.add_argument("--collection-appid", default=TARGET_COLLECTION_APPID)
    ap.add_argument("--state", default=DEFAULT_STATE)
    ap.add_argument("--workers", type=int, default=4)
    ap.add_argument("--limit", type=int, default=None, help="only first N files")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--verbose", action="store_true")
    args = ap.parse_args()

    api_key = load_api_key(args.api_key, args.keyfile)
    client = Client(args.host, api_key, verbose=args.verbose)

    print(f"source dir    : {args.source_dir}")
    print(f"host          : {args.host}")
    print(f"collection    : {args.collection_appid}")
    print(f"state file    : {args.state}")
    print(f"mode          : {'DRY-RUN' if args.dry_run else 'LIVE'}  workers={args.workers}"
          f"{'  limit='+str(args.limit) if args.limit else ''}")

    me = client.get_json("/v2/users/me")
    print(f"auth ok       : {me.get('effectiveDisplayName')} ({me.get('appId')})")
    col = client.get_json(f"/v2/collections/{args.collection_appid}")
    print(f"collection ok : {col.get('name')}")

    files = discover_svdx(args.source_dir)
    if args.limit:
        files = files[: args.limit]
    total_bytes = sum(os.path.getsize(f) for f in files)
    print(f"svdx files    : {len(files)}  ({total_bytes/1e9:.2f} GB)\n")
    if not files:
        sys.stderr.write("ERROR: no .svdx files found\n")
        sys.exit(1)

    state = State(args.state)
    counts = Counts()

    live = None
    if not args.dry_run:
        live = LiveIndex(client, args.collection_appid, workers=args.workers)
        live.build(verbose=True)
        print(f"live index    : {len(live.by_name)} existing DOs, "
              f"{len(live.by_source_id)} source-id annotations, "
              f"{sum(len(v) for v in live.file_refs_by_do.values())} file refs")

    imp = Importer(client, state, counts, args.collection_appid,
                   args.dry_run, args.verbose, live=live)

    if args.dry_run:
        for f in files:
            imp.import_file(f)
        _print_summary(counts, dry=True)
        return

    errors: list[tuple[str, str]] = []
    done = 0
    total = len(files)
    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        futs = {ex.submit(imp.import_file, f): os.path.basename(f) for f in files}
        for fut in as_completed(futs):
            name = futs[fut]
            try:
                fut.result()
            except Exception as e:
                errors.append((name, str(e)))
                print(f"  [HARD ERROR] {name}: {e}", file=sys.stderr, flush=True)
            done += 1
            print(f"  progress: {done}/{total}  "
                  f"(created={counts.dos_created} reused={counts.dos_reused} "
                  f"files={counts.file_refs} bytes={counts.bytes_uploaded/1e9:.2f}GB)",
                  flush=True)

    _print_summary(counts, dry=False)
    if errors:
        print(f"\nHARD ERRORS ({len(errors)}):")
        for name, e in errors[:50]:
            print(f"  {name}: {e}")
        sys.exit(1)


def _print_summary(c: Counts, dry: bool):
    print("\n── summary " + ("(DRY-RUN plan)" if dry else "(live run)") + " ──")
    print(f"  DataObjects created     : {c.dos_created}")
    if not dry:
        print(f"  DataObjects reused      : {c.dos_reused}")
    print(f"  file refs               : {c.file_refs}")
    if not dry:
        print(f"  file refs reused        : {c.file_refs_reused}")
    print(f"  payload bytes           : {c.bytes_uploaded} ({c.bytes_uploaded/1e9:.2f} GB)")
    if not dry:
        print(f"  provenance annotations  : {c.annotations}")
        print(f"  annotation failures     : {c.annotation_failures}")
        print("  (svdx:* annotations are written server-side by the fileformat-svdx parser)")


if __name__ == "__main__":
    main()
