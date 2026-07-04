#!/usr/bin/env python3
"""
mffd-bridge-replay.py — replay a Shepard-export manifest into a live Shepard
instance, populating the ``mffd-bridge-welding`` Collection from LOCAL data.

This validates the MFFD "bridge welding" import wave on a freshly-reset
instance. ALL payload bytes are read from local disk — the manifest's
``source_url`` is informational and is NEVER fetched.

Source
------
- Manifest: ``/mnt/pve/unas/dump/dataset/4-Brückenschweißen/manifest.json``
- Payload root: the manifest's own directory (``file`` paths are relative to it,
  e.g. ``bridgewelding/AF_9/structured/StepMetaProcessStep.json``).

Target
------
- Collection appId ``019ed455-6781-755e-87dd-eb3f2f3dbba3`` (``mffd-bridge-welding``).
- Host ``https://shepard-api.nuclide.systems`` (override with ``--host``).
- Auth header ``X-API-KEY: <jwt>`` (``--api-key`` / ``SHEPARD_API_KEY`` env /
  the key file). The key carries ``instance-admin``.

API surface
-----------
Builds on the unified v2 kind-discriminated surface where one exists, addressing
entities by ``appId``:

- DataObject create: ``POST /v2/collections/{collectionAppId}/data-objects``
  (returns ``appId`` AND numeric ``id``).
- File reference: ``POST /v2/references?kind=file&dataObjectAppId=…`` with
  ``{name}`` → then ``POST /v2/references/{appId}/content?filename=…`` (octet-stream).
- Annotations: ``POST /v2/annotations`` with
  ``{subjectAppId, subjectKind, predicateIri, objectLiteral}``.

Structured-data has NO v2 reference/payload-upload counterpart yet (the unified
``/v2/references`` surface has no ``structured-data`` kind handler, and the
structured-data container POST-payload endpoint is v1-only). So the structured
flow uses the frozen v1 surface (numeric ids), documented inline. This is a
genuine v2 API gap — see the report / aidocs/16.

Completeness + resumability
---------------------------
- Never skips a DataObject or reference on a transient error: retry-forever with
  exponential backoff on 5xx / timeouts / connection errors.
- Progress is persisted to a local sqlite state file (default
  ``~/.mffd-bridge-replay-state.json.db``) keyed by ``do_id`` / ``ref_id`` so a
  re-run continues where it left off and never double-creates.

Idempotency against LIVE Shepard (not just the sqlite state)
-----------------------------------------------------------
The sqlite state alone is NOT a sufficient idempotency layer: if the state file
is wiped between runs (or a different machine runs the import) the importer would
re-create DataObjects that already exist in Shepard. That defect produced
duplicate DataObjects on 2026-06-17. The fix, layered on top of the state:

- **Stable source id.** Every DataObject gets a semantic annotation
  ``urn:shepard:source:cube3-do-id = <do_id>`` so it can be found again on any
  future run regardless of the local state.
- **Live index built at startup.** Before the run, the importer pulls every live
  DataObject in the target collection (by name) AND every source-id annotation
  AND every DataObject's existing file-reference labels — one settled snapshot.
- **Reuse-before-create.** A DataObject is looked up in that index (by source-id,
  then exact name) and REUSED if present; only a genuine miss triggers a create.
- **Skip-if-present refs.** File and structured references already attached to a
  reused DataObject (under the same label/name) are skipped — a fresh confirming
  re-read guards against the ``by-data-object`` projection's eventual-consistency
  lag, which would otherwise re-upload a handful of files.
- **Annotation writes are guarded** by the source-id index so a re-run writes
  zero annotations.

Net effect: running the importer N times converges to exactly the manifest's
contents and the (N>1) runs create ZERO new graph nodes. Verified against Neo4j
directly (live, non-``deleted`` nodes), never against the importer's own state.

Usage
-----
    python3 mffd-bridge-replay.py --dry-run
    python3 mffd-bridge-replay.py --limit 3 --verbose
    python3 mffd-bridge-replay.py            # full run, all 1031 DOs
"""
from __future__ import annotations

import argparse
import json
import os
import sqlite3
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from typing import Any, Optional

try:
    import requests
except ImportError:
    sys.stderr.write("ERROR: this script needs `requests`.  pip install requests\n")
    sys.exit(2)

# ── defaults ──────────────────────────────────────────────────────────────────
DEFAULT_HOST = "https://shepard-api.nuclide.systems"
DEFAULT_MANIFEST = "/mnt/pve/unas/dump/dataset/4-Brückenschweißen/manifest.json"
DEFAULT_KEYFILE = "/root/.claude/uploads/mffd-import-key-2026-06-17.txt"
TARGET_COLLECTION_APPID = "019ed455-6781-755e-87dd-eb3f2f3dbba3"
PROVENANCE_PREDICATE = "urn:shepard:source:provenance"
# Stable cross-run source identifier on every DataObject. Lets a re-run find an
# already-imported DO in LIVE Shepard (not just the local sqlite state) and reuse
# it instead of double-creating — the fix for the duplication this importer caused.
SOURCE_DO_ID_PREDICATE = "urn:shepard:source:cube3-do-id"
DEFAULT_STATE = os.path.expanduser("~/.mffd-bridge-replay-state.db")
DATASET_ROOT_MARKER = "4-Brückenschweißen"  # provenance paths are dataset-relative from here

# ── small thread-safe sqlite state store ───────────────────────────────────────
class State:
    """Resumable progress store. Maps logical keys → created appIds/ids.

    Tables:
      dos(do_id INTEGER PK, do_appid TEXT, do_numeric_id INTEGER, annotated INTEGER)
      struct_refs(do_id INT, ref_name TEXT, file TEXT, container_id INTEGER,
                  oid TEXT, ref_id INTEGER, annotated INTEGER, PK(do_id,ref_name,file))
      file_refs(ref_id INTEGER, file TEXT, ref_appid TEXT, uploaded INTEGER,
                annotated INTEGER, PK(ref_id, file))   -- one cube3 fref_id can
                fan out to N physical files (.0/.1/.2/.3 multi-file bundle)
      ts_refs(ref_id INTEGER PK, recorded TEXT)   -- unhandled-format log
    """

    def __init__(self, path: str):
        self._lock = threading.Lock()
        self._conn = sqlite3.connect(path, check_same_thread=False)
        self._conn.execute("PRAGMA journal_mode=WAL")
        cur = self._conn.cursor()
        cur.executescript(
            """
            CREATE TABLE IF NOT EXISTS dos(
              do_id INTEGER PRIMARY KEY, do_appid TEXT, do_numeric_id INTEGER,
              annotated INTEGER DEFAULT 0);
            CREATE TABLE IF NOT EXISTS struct_refs(
              do_id INTEGER, ref_name TEXT, file TEXT,
              container_id INTEGER, oid TEXT, ref_id INTEGER, ref_appid TEXT,
              annotated INTEGER DEFAULT 0,
              PRIMARY KEY(do_id, ref_name, file));
            CREATE TABLE IF NOT EXISTS file_refs(
              ref_id INTEGER, file TEXT, ref_appid TEXT,
              uploaded INTEGER DEFAULT 0, annotated INTEGER DEFAULT 0,
              PRIMARY KEY(ref_id, file));
            CREATE TABLE IF NOT EXISTS ts_refs(
              ref_id INTEGER PRIMARY KEY, recorded TEXT);
            """
        )
        self._conn.commit()

    # -- dos
    def get_do(self, do_id: int):
        with self._lock:
            r = self._conn.execute(
                "SELECT do_appid, do_numeric_id, annotated FROM dos WHERE do_id=?", (do_id,)
            ).fetchone()
        return r

    def put_do(self, do_id: int, appid: str, numeric_id: int):
        with self._lock:
            self._conn.execute(
                "INSERT INTO dos(do_id,do_appid,do_numeric_id) VALUES(?,?,?) "
                "ON CONFLICT(do_id) DO UPDATE SET do_appid=excluded.do_appid, "
                "do_numeric_id=excluded.do_numeric_id",
                (do_id, appid, numeric_id),
            )
            self._conn.commit()

    def mark_do_annotated(self, do_id: int):
        with self._lock:
            self._conn.execute("UPDATE dos SET annotated=1 WHERE do_id=?", (do_id,))
            self._conn.commit()

    # -- structured refs
    def get_struct(self, do_id: int, ref_name: str, file: str):
        with self._lock:
            return self._conn.execute(
                "SELECT container_id, oid, ref_id, ref_appid, annotated FROM struct_refs "
                "WHERE do_id=? AND ref_name=? AND file=?",
                (do_id, ref_name, file),
            ).fetchone()

    def put_struct(self, do_id, ref_name, file, container_id, oid, ref_id, ref_appid=None):
        with self._lock:
            self._conn.execute(
                "INSERT INTO struct_refs(do_id,ref_name,file,container_id,oid,ref_id,ref_appid) "
                "VALUES(?,?,?,?,?,?,?) ON CONFLICT(do_id,ref_name,file) DO UPDATE SET "
                "container_id=excluded.container_id, oid=excluded.oid, "
                "ref_id=excluded.ref_id, ref_appid=excluded.ref_appid",
                (do_id, ref_name, file, container_id, oid, ref_id, ref_appid),
            )
            self._conn.commit()

    def mark_struct_annotated(self, do_id, ref_name, file):
        with self._lock:
            self._conn.execute(
                "UPDATE struct_refs SET annotated=1 WHERE do_id=? AND ref_name=? AND file=?",
                (do_id, ref_name, file),
            )
            self._conn.commit()

    # -- file refs (keyed by (ref_id, file): a cube3 fref_id may fan out to N files)
    def get_file(self, ref_id: int, file: str):
        with self._lock:
            return self._conn.execute(
                "SELECT ref_appid, uploaded, annotated FROM file_refs "
                "WHERE ref_id=? AND file=?", (ref_id, file)
            ).fetchone()

    def put_file(self, ref_id, file, ref_appid, uploaded=0):
        with self._lock:
            self._conn.execute(
                "INSERT INTO file_refs(ref_id,file,ref_appid,uploaded) VALUES(?,?,?,?) "
                "ON CONFLICT(ref_id,file) DO UPDATE SET ref_appid=excluded.ref_appid, "
                "uploaded=MAX(file_refs.uploaded, excluded.uploaded)",
                (ref_id, file, ref_appid, uploaded),
            )
            self._conn.commit()

    def mark_file_uploaded(self, ref_id, file):
        with self._lock:
            self._conn.execute(
                "UPDATE file_refs SET uploaded=1 WHERE ref_id=? AND file=?", (ref_id, file))
            self._conn.commit()

    def mark_file_annotated(self, ref_id, file):
        with self._lock:
            self._conn.execute(
                "UPDATE file_refs SET annotated=1 WHERE ref_id=? AND file=?", (ref_id, file))
            self._conn.commit()

    def record_ts(self, ref_id, note):
        with self._lock:
            self._conn.execute(
                "INSERT OR REPLACE INTO ts_refs(ref_id,recorded) VALUES(?,?)", (ref_id, note)
            )
            self._conn.commit()


# ── HTTP client with retry-forever backoff ─────────────────────────────────────
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
        """Retry-forever on connection/timeout/5xx. A genuine 4xx is a hard
        error (the data is bad, not the transport) — EXCEPT a transient 403 on
        a write right after DataObject creation, where the :Permissions node
        wiring can briefly lag. With ``retry_on_403`` such a 403 is retried with
        bounded backoff before being surfaced."""
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
            # genuine client error — surface it (do not silently skip)
            raise RuntimeError(
                f"{method} {path} -> {resp.status_code}: {resp.text[:400]}"
            )

    def get_json(self, path, **kw):
        return self.request("GET", path, **kw).json()

    def post_json(self, path, body, *, retry_on_403=False, **kw):
        return self.request("POST", path, json=body, expect=(200, 201),
                            retry_on_403=retry_on_403, **kw).json()


# ── replay logic ───────────────────────────────────────────────────────────────
@dataclass
class Counts:
    dos_created: int = 0
    dos_reused: int = 0
    struct_refs: int = 0
    struct_dupes_collapsed: int = 0
    file_refs: int = 0
    bytes_uploaded: int = 0
    annotations: int = 0
    annotation_failures: int = 0
    ts_unhandled: int = 0
    _lock: threading.Lock = field(default_factory=threading.Lock, repr=False)

    def add(self, **kw):
        with self._lock:
            for k, v in kw.items():
                setattr(self, k, getattr(self, k) + v)


class LiveIndex:
    """A snapshot of what already exists in LIVE Shepard for the target
    collection, built once at startup. This is the idempotency-against-live
    layer the sqlite state alone could not provide: even with a wiped state
    file, a re-run reuses the existing DataObjects instead of re-creating them.

    Indexes (only non-deleted entities are returned by the list endpoints, so
    soft-delete tombstones never collide with a live reuse):
      by_source_id : urn:shepard:source:cube3-do-id literal -> DO appId
      by_name      : do_name -> DO appId  (fallback when source-id absent)
    """

    def __init__(self, client: Client, collection_appid: str, workers: int = 6):
        self.c = client
        self.col = collection_appid
        self.workers = workers
        self.by_source_id: dict[str, str] = {}
        self.by_name: dict[str, str] = {}
        # do_appid -> set of existing file-reference labels, pre-loaded from a
        # SETTLED snapshot at startup. Reading the file-ref index up front (when
        # the data is quiescent) avoids the eventual-consistency lag that the
        # lazy per-DO reads hit mid-run — the source of the file-ref dupes.
        self.file_refs_by_do: dict[str, set] = {}

    def build(self, verbose: bool = False):
        # 1. all live DataObjects in the collection, keyed by name
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
                # last write wins; live list never returns duplicate live names
                self.by_name[d["name"]] = d["appId"]
                do_appids.append(d["appId"])
            n += len(batch)
            page += 1
        # 1b. pre-load each live DO's existing file-reference labels from this
        # settled snapshot (parallel for speed). Reused later as the skip-check.
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
        # 2. all source-id annotations -> map literal source id to subject appId.
        # The annotations endpoint caps pageSize at 200 (data-objects allows 500).
        page = 0
        anns = 0
        while True:
            batch = self.c.get_json(
                f"/v2/annotations?predicateIri={requests.utils.quote(SOURCE_DO_ID_PREDICATE)}"
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

    def find_do(self, do_id: int, do_name: str) -> Optional[str]:
        """Return an existing live DO appId for this cube3 do_id (by source-id
        annotation first, then exact name), or None if it must be created."""
        hit = self.by_source_id.get(str(do_id))
        if hit:
            return hit
        return self.by_name.get(do_name)


class Replayer:
    def __init__(self, client: Client, state: State, counts: Counts,
                 collection_appid: str, collection_numeric_id: int,
                 payload_root: str, dry_run: bool, verbose: bool,
                 live: Optional["LiveIndex"] = None):
        self.c = client
        self.st = state
        self.n = counts
        self.col_appid = collection_appid
        self.col_id = collection_numeric_id
        self.root = payload_root
        self.dry = dry_run
        self.verbose = verbose
        self.live = live
        self._ann_kind_warned = False
        # per-DO cache of reference names already present in live Shepard, fetched
        # lazily the first time a reused DO needs a skip-check. Guards against
        # re-creating refs on a DO that the live index matched but whose refs are
        # not yet recorded in the (possibly wiped) sqlite state.
        self._live_refs_lock = threading.Lock()
        self._live_struct_refs: dict[str, set[str]] = {}
        # seed the file-ref skip cache from the live index's settled startup
        # snapshot so the lazy mid-run reads (and their consistency lag) are not
        # the first source of truth.
        self._live_file_refs: dict[str, set[str]] = (
            {k: set(v) for k, v in live.file_refs_by_do.items()} if live else {}
        )
        # Minimal-containers: ONE shared StructuredDataContainer backs every
        # structured-data reference for the whole collection (was one per ref,
        # ~3102). Resolved once. Payloads coexist in it; each reference selects
        # its own oid(s).
        self._shared_sdc: Optional[int] = None
        self._shared_sdc_lock = threading.Lock()

    SHARED_SDC_NAME = "mffd-bridge-structured"

    def _shared_structured_container(self) -> int:
        """Idempotently resolve the ONE shared StructuredDataContainer (by name)
        backing every structured-data reference. Created + PublicReadable once."""
        with self._shared_sdc_lock:
            if self._shared_sdc is not None:
                return self._shared_sdc
            try:
                existing = self.c.get_json(
                    f"/shepard/api/structuredDataContainers?name={self.SHARED_SDC_NAME}")
                for sdc in (existing or []):
                    if sdc.get("name") == self.SHARED_SDC_NAME and sdc.get("id"):
                        self._shared_sdc = sdc["id"]
                        return self._shared_sdc
            except RuntimeError:
                pass
            sdc = self.c.post_json("/shepard/api/structuredDataContainers",
                                   {"name": self.SHARED_SDC_NAME})
            self._shared_sdc = sdc["id"]
            try:
                cur = self.c.get_json(
                    f"/shepard/api/structuredDataContainers/{self._shared_sdc}/permissions")
                cur["permissionType"] = "PublicReadable"
                self.c.request("PUT",
                    f"/shepard/api/structuredDataContainers/{self._shared_sdc}/permissions",
                    json=cur, expect=(200, 201, 204))
            except RuntimeError:
                pass
            print(f"  shared SDC #{self._shared_sdc} ({self.SHARED_SDC_NAME}) PublicReadable",
                  flush=True)
            return self._shared_sdc

    def _existing_struct_ref_names(self, do_appid: str, do_numeric: int) -> set:
        with self._live_refs_lock:
            cached = self._live_struct_refs.get(do_appid)
        if cached is not None:
            return cached
        names = set()
        try:
            lst = self.c.get_json(
                f"/shepard/api/collections/{self.col_id}/dataObjects/{do_numeric}"
                f"/structuredDataReferences"
            )
            names = {x.get("name") for x in lst if x.get("name")}
        except RuntimeError as e:
            if self.verbose:
                print(f"  [live-refs WARN] struct list {do_appid}: {e}",
                      file=sys.stderr, flush=True)
        with self._live_refs_lock:
            self._live_struct_refs[do_appid] = names
        return names

    def _existing_file_ref_names(self, do_appid: str, refresh: bool = False) -> set:
        if not refresh:
            with self._live_refs_lock:
                cached = self._live_file_refs.get(do_appid)
            if cached is not None:
                return cached
        names = set()
        try:
            lst = self.c.get_json(f"/v2/files/by-data-object/{do_appid}")
            if isinstance(lst, list):
                names = {x.get("name") for x in lst if x.get("name")}
        except RuntimeError as e:
            if self.verbose:
                print(f"  [live-refs WARN] file list {do_appid}: {e}",
                      file=sys.stderr, flush=True)
        with self._live_refs_lock:
            # merge rather than overwrite, so a refresh never drops names we
            # already recorded from a successful create this run
            merged = self._live_file_refs.get(do_appid, set()) | names
            self._live_file_refs[do_appid] = merged
            return merged

    def _note_live_struct(self, do_appid: str, name: str):
        with self._live_refs_lock:
            self._live_struct_refs.setdefault(do_appid, set()).add(name)

    def _note_live_file(self, do_appid: str, name: str):
        with self._live_refs_lock:
            self._live_file_refs.setdefault(do_appid, set()).add(name)

    def _abs(self, relpath: str) -> str:
        return os.path.join(self.root, relpath)

    def _resolve_do_numeric(self, do_appid: str, do_name: str) -> int:
        """Resolve a DataObject's numeric id from its appId via the frozen v1
        collection-DO list (name-filtered, matched on appId). v1-only because
        the v2 DO IO suppresses the numeric id and the structured-data ref
        create endpoint has no v2 counterpart."""
        lst = self.c.get_json(
            f"/shepard/api/collections/{self.col_id}/dataObjects"
            f"?name={requests.utils.quote(do_name)}"
        )
        for d in lst:
            if d.get("appId") == do_appid:
                return d["id"]
        raise RuntimeError(f"could not resolve numeric id for DO appId={do_appid}")

    def _prov_path(self, relpath: str) -> str:
        """Dataset-relative provenance value: <marker>/<relpath>."""
        return f"{DATASET_ROOT_MARKER}/{relpath}"

    def live_source_ids(self) -> dict:
        """The cube3-do-id literals already annotated in live Shepard."""
        return self.live.by_source_id if self.live else {}

    # -- annotations (best-effort secondary write; never aborts the import) ------
    def _annotate(self, subject_appid: str, subject_kind: str, prov_value: str,
                  predicate: str = PROVENANCE_PREDICATE) -> bool:
        if self.dry:
            return True
        body = {
            "subjectAppId": subject_appid,
            "subjectKind": subject_kind,
            "predicateIri": predicate,
            "objectLiteral": prov_value,
            "sourceMode": "ai",
        }
        try:
            self.c.post_json("/v2/annotations", body)
            self.n.add(annotations=1)
            return True
        except RuntimeError as e:
            # secondary write — log + record, do not fail the import (fire-and-forget)
            self.n.add(annotation_failures=1)
            if self.verbose:
                print(f"  [annotation WARN] {subject_kind} {subject_appid}: {e}",
                      file=sys.stderr, flush=True)
            return False

    # -- one DataObject end to end ----------------------------------------------
    def replay_do(self, name: str, do: dict):
        do_id = do["do_id"]
        do_name = do.get("do_name", name)

        if self.dry:
            self._plan_do(name, do)
            return

        # 1. DataObject — idempotent against (a) local sqlite state, then
        # (b) LIVE Shepard via the source-id/name index. Only when both miss do
        # we create. This is the fix for the duplication: a wiped state file no
        # longer causes a re-create of a DO that already exists in Shepard.
        cached = self.st.get_do(do_id)
        annotated = 0
        do_appid = do_numeric = None
        if cached and cached[0]:
            do_appid, do_numeric, annotated = cached
            self.n.add(dos_reused=1)
        else:
            live_appid = self.live.find_do(do_id, do_name) if self.live else None
            if live_appid:
                # Found in live Shepard — reuse, don't create. Seed local state.
                do_appid = live_appid
                do_numeric = self._resolve_do_numeric(do_appid, do_name)
                self.st.put_do(do_id, do_appid, do_numeric)
                self.n.add(dos_reused=1)
            else:
                # DataObject creation stays on the v2 surface (appId-native).
                created = self.c.post_json(
                    f"/v2/collections/{self.col_appid}/data-objects",
                    {"name": do_name},
                )
                do_appid = created["appId"]
                self.n.add(dos_created=1)
                # The v2 DO IO suppresses the numeric id; resolve it via the
                # frozen v1 collection list (needed only for the v1
                # structured-data ref create). Cached in state so re-runs skip it.
                do_numeric = self._resolve_do_numeric(do_appid, do_name)
                self.st.put_do(do_id, do_appid, do_numeric)
                # Keep the NAME index current so a same-run sibling name reuses
                # it. The source-id index is intentionally NOT seeded here — it
                # tracks "source-id annotation written", and is set only after the
                # annotation POST below succeeds. Seeding it here would make the
                # `not in live_source_ids()` guard skip writing the annotation.
                if self.live:
                    self.live.by_name[do_name] = do_appid

        # provenance + stable source-id annotations on the DataObject.
        # BOTH writes are guarded by the live source-id index: a DO that already
        # carries its cube3-do-id annotation has already been fully annotated, so
        # a re-run writes ZERO annotations (the source-id annotation is written
        # last, so its presence proves provenance was written too). This is what
        # makes the annotation pass idempotent — without this guard, provenance
        # annotations duplicated on every re-run.
        already_annotated = self.live and str(do_id) in self.live_source_ids()
        if not annotated and not already_annotated:
            prov = f"{DATASET_ROOT_MARKER}/bridgewelding/{do_name}"
            self._annotate(do_appid, "DataObject", prov)
            self._annotate(do_appid, "DataObject", str(do_id),
                           predicate=SOURCE_DO_ID_PREDICATE)
            if self.live:
                # mark fully-annotated only after both writes are issued
                self.live.by_source_id[str(do_id)] = do_appid
            self.st.mark_do_annotated(do_id)

        # 2. structured refs — collapse exact (ref_name, file) duplicates
        seen: set[tuple[str, str]] = set()
        for r in do.get("structured_refs", []):
            key = (r["ref_name"], r["file"])
            if key in seen:
                self.n.add(struct_dupes_collapsed=1)
                continue
            seen.add(key)
            self._replay_struct(do_id, do_appid, do_numeric, r)

        # 3. file refs — all distinct
        for r in do.get("file_refs", []):
            self._replay_file(do_appid, r)

        # 4. ts refs — none expected in the bridge corpus; record if present
        for r in do.get("ts_refs", []):
            self._replay_ts(do_appid, r)

    def _replay_struct(self, do_id, do_appid, do_numeric, r):
        ref_name = r["ref_name"]
        relfile = r["file"]
        cached = self.st.get_struct(do_id, ref_name, relfile)
        container_id = oid = ref_id = ref_appid = None
        annotated = 0
        if cached:
            container_id, oid, ref_id, ref_appid, annotated = cached

        # Live skip-if-present: if sqlite has no record of this ref (e.g. wiped
        # state on a reused DO), check live Shepard before creating. A struct ref
        # already attached to this DO under the same name is a no-op.
        if not cached and ref_name in self._existing_struct_ref_names(do_appid, do_numeric):
            self.n.add(struct_refs=1)
            return

        abspath = self._abs(relfile)
        if not os.path.exists(abspath):
            raise FileNotFoundError(f"structured payload missing on disk: {abspath}")

        # v1-only flow: no v2 structured-data reference/payload-upload endpoint
        # exists yet (the unified /v2/references surface has no structured-data
        # kind handler, and the structured-data payload POST is v1-only).
        # Documented gap — see report + aidocs/16 PLUGIN-V2 rows.
        if container_id is None:
            # Minimal-containers: all structured-data refs share ONE SDC.
            container_id = self._shared_structured_container()
            self.st.put_struct(do_id, ref_name, relfile, container_id, None, None, None)

        if oid is None:
            with open(abspath, "r", encoding="utf-8") as fh:
                raw = fh.read()
            sd = self.c.post_json(
                f"/shepard/api/structuredDataContainers/{container_id}/payload",
                {"payload": raw},
            )
            # response is a StructuredData (carries oid), possibly nested
            oid = sd.get("oid") if isinstance(sd, dict) else None
            if oid is None and isinstance(sd, dict):
                oid = (sd.get("structuredData") or {}).get("oid")
            self.st.put_struct(do_id, ref_name, relfile, container_id, oid, None, None)

        if ref_id is None:
            ref = self.c.post_json(
                f"/shepard/api/collections/{self.col_id}/dataObjects/{do_numeric}/structuredDataReferences",
                {
                    "name": ref_name,
                    "structuredDataContainerId": container_id,
                    "structuredDataOids": [oid],
                },
                retry_on_403=True,  # tolerate :Permissions wiring lag post-DO-create
            )
            ref_id = ref.get("id")
            ref_appid = ref.get("appId")  # BasicEntityIO base carries appId
            self.st.put_struct(do_id, ref_name, relfile, container_id, oid, ref_id, ref_appid)
            self._note_live_struct(do_appid, ref_name)

        self.n.add(struct_refs=1)

        # provenance annotation on the reference (v2 annotations key on appId)
        if not annotated:
            if ref_appid:
                self._annotate(ref_appid, "Reference", self._prov_path(relfile))
            self.st.mark_struct_annotated(do_id, ref_name, relfile)

    def _replay_file(self, do_appid, r):
        # file_refs key the original cube3 reference id as `fref_id`
        # (structured_refs use `ref_id`); accept either.
        ref_id = r.get("fref_id", r.get("ref_id"))
        ref_name = r["ref_name"]
        relfile = r["file"]
        abspath = self._abs(relfile)
        if not os.path.exists(abspath):
            raise FileNotFoundError(f"file payload missing on disk: {abspath}")

        cached = self.st.get_file(ref_id, relfile)
        ref_appid = None
        uploaded = annotated = 0
        if cached:
            ref_appid, uploaded, annotated = cached

        size = os.path.getsize(abspath)
        filename = os.path.basename(relfile)
        ref_label = ref_name if filename == ref_name else f"{ref_name}/{filename}"

        # Live skip-if-present: if sqlite has no record (wiped state on a reused
        # DO), check live Shepard before re-uploading. A singleton file ref under
        # the same label already attached to this DO is a no-op. If the cached
        # list does NOT contain the label, do one FRESH confirming re-read before
        # committing to a create — the /v2/files/by-data-object index can lag
        # right after a prior run's upload, and trusting a single stale/empty read
        # is exactly what produced 24 duplicate file refs on the first re-run.
        if not cached:
            if ref_label in self._existing_file_ref_names(do_appid):
                self.n.add(file_refs=1)
                return
            if ref_label in self._existing_file_ref_names(do_appid, refresh=True):
                self.n.add(file_refs=1)
                return

        # Single physical file → singleton FileReference (FR1b): multipart
        # POST /v2/files creates the reference AND uploads the bytes in one call
        # (the unified /v2/references?kind=file path rejects binary kinds by
        # design). A cube3 fref_id that fanned out to .0/.1/.2/.3 yields one
        # singleton per physical file — no pointless bundle/selection layer.
        # Disambiguate the per-file ref name with the file's basename suffix.
        if not ref_appid or not uploaded:
            with open(abspath, "rb") as fh:
                resp = self.c.request(
                    "POST",
                    f"/v2/files?name={requests.utils.quote(ref_label)}"
                    f"&parentDataObjectAppId={do_appid}",
                    files={"file": (filename, fh, "application/octet-stream")},
                    expect=(200, 201),
                    retry_on_403=True,  # tolerate :Permissions wiring lag post-DO-create
                )
            created = resp.json()
            ref_appid = created["appId"]
            self.st.put_file(ref_id, relfile, ref_appid, uploaded=1)
            self._note_live_file(do_appid, ref_label)
            self.n.add(bytes_uploaded=size)

        self.n.add(file_refs=1)

        # 3. provenance annotation on the file reference
        if not annotated:
            self._annotate(ref_appid, "Reference", self._prov_path(relfile))
            self.st.mark_file_annotated(ref_id, relfile)

    def _replay_ts(self, do_appid, r):
        # The bridge corpus has no ts_refs; if one appears the payload format is
        # undefined here. Record it (do NOT skip silently) and continue.
        note = f"ts_ref present but format undefined: {json.dumps(r)[:300]}"
        self.st.record_ts(r.get("ref_id", -1), note)
        self.n.add(ts_unhandled=1)
        print(f"  [TS UNHANDLED] do={do_appid} {note}", file=sys.stderr, flush=True)

    # -- dry-run accounting ------------------------------------------------------
    def _plan_do(self, name, do):
        self.n.add(dos_created=1)
        seen = set()
        for r in do.get("structured_refs", []):
            key = (r["ref_name"], r["file"])
            if key in seen:
                self.n.add(struct_dupes_collapsed=1)
                continue
            seen.add(key)
            self.n.add(struct_refs=1)
            abspath = self._abs(r["file"])
            if os.path.exists(abspath):
                self.n.add(bytes_uploaded=os.path.getsize(abspath))
        for r in do.get("file_refs", []):
            self.n.add(file_refs=1)
            abspath = self._abs(r["file"])
            if os.path.exists(abspath):
                self.n.add(bytes_uploaded=os.path.getsize(abspath))
        for r in do.get("ts_refs", []):
            self.n.add(ts_unhandled=1)


# ── orchestration ───────────────────────────────────────────────────────────────
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


def main():
    ap = argparse.ArgumentParser(description="Replay an MFFD bridge-welding export manifest into Shepard.")
    ap.add_argument("--manifest", default=DEFAULT_MANIFEST)
    ap.add_argument("--host", default=DEFAULT_HOST)
    ap.add_argument("--api-key", default=None)
    ap.add_argument("--keyfile", default=DEFAULT_KEYFILE)
    ap.add_argument("--collection-appid", default=TARGET_COLLECTION_APPID)
    ap.add_argument("--state", default=DEFAULT_STATE)
    ap.add_argument("--workers", type=int, default=6)
    ap.add_argument("--limit", type=int, default=None, help="only first N DataObjects")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--verbose", action="store_true")
    args = ap.parse_args()

    api_key = load_api_key(args.api_key, args.keyfile)
    client = Client(args.host, api_key, verbose=args.verbose)

    # payload root = manifest's directory
    payload_root = os.path.dirname(os.path.abspath(args.manifest))

    print(f"manifest      : {args.manifest}")
    print(f"payload root  : {payload_root}")
    print(f"host          : {args.host}")
    print(f"collection    : {args.collection_appid}")
    print(f"state file    : {args.state}")
    print(f"mode          : {'DRY-RUN' if args.dry_run else 'LIVE'}  workers={args.workers}"
          f"{'  limit='+str(args.limit) if args.limit else ''}")

    # verify auth + collection (skip live calls only matters when not dry; we
    # still verify in dry-run to fail fast on a bad key)
    me = client.get_json("/v2/users/me")
    print(f"auth ok       : {me.get('effectiveDisplayName')} ({me.get('appId')})")
    col = client.get_json(f"/v2/collections/{args.collection_appid}")
    print(f"collection ok : {col.get('name')}")

    # resolve collection numeric id (needed for the v1 structured-data ref create)
    col_list = client.get_json(
        f"/shepard/api/collections?name={requests.utils.quote(col['name'])}"
    )
    col_numeric = next((c["id"] for c in col_list if c.get("appId") == args.collection_appid), None)
    if col_numeric is None:
        sys.stderr.write("ERROR: could not resolve collection numeric id\n")
        sys.exit(1)
    print(f"collection #id: {col_numeric}")

    manifest = json.load(open(args.manifest, encoding="utf-8"))
    dos = manifest["collections"]["bridgewelding"]["dos"]
    items = list(dos.items())
    if args.limit:
        items = items[: args.limit]
    print(f"DataObjects   : {len(items)}\n")

    state = State(args.state)
    counts = Counts()

    # Build the live-Shepard index (skipped in dry-run): existing DOs by name and
    # by source-id annotation. This is what makes the importer idempotent against
    # LIVE state, not just its own sqlite — converging to the manifest no matter
    # how many times it runs, even with a wiped state file.
    live = None
    if not args.dry_run:
        live = LiveIndex(client, args.collection_appid, workers=args.workers)
        live.build(verbose=True)
        print(f"live index    : {len(live.by_name)} existing DOs, "
              f"{len(live.by_source_id)} source-id annotations, "
              f"{sum(len(v) for v in live.file_refs_by_do.values())} file refs")

    rep = Replayer(client, state, counts, args.collection_appid, col_numeric,
                   payload_root, args.dry_run, args.verbose, live=live)

    if args.dry_run:
        for name, do in items:
            rep.replay_do(name, do)
        _print_summary(counts, dry=True)
        return

    errors: list[tuple[str, str]] = []
    done = 0
    total = len(items)
    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        futs = {ex.submit(rep.replay_do, name, do): name for name, do in items}
        for fut in as_completed(futs):
            name = futs[fut]
            try:
                fut.result()
            except Exception as e:  # retry-forever exhausted only on hard 4xx
                errors.append((name, str(e)))
                print(f"  [HARD ERROR] DO {name}: {e}", file=sys.stderr, flush=True)
            done += 1
            if done % 25 == 0 or done == total:
                print(f"  progress: {done}/{total} DOs  "
                      f"(created={counts.dos_created} reused={counts.dos_reused} "
                      f"struct={counts.struct_refs} file={counts.file_refs} "
                      f"bytes={counts.bytes_uploaded/1e9:.2f}GB)", flush=True)

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
    print(f"  structured refs         : {c.struct_refs}")
    print(f"  structured dupes collapsed: {c.struct_dupes_collapsed}")
    print(f"  file refs               : {c.file_refs}")
    print(f"  payload bytes           : {c.bytes_uploaded} ({c.bytes_uploaded/1e9:.2f} GB)")
    if not dry:
        print(f"  annotations written     : {c.annotations}")
        print(f"  annotation failures     : {c.annotation_failures}")
    print(f"  ts refs unhandled       : {c.ts_unhandled}")


if __name__ == "__main__":
    main()
