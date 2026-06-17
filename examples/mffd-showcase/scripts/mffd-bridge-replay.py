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
DEFAULT_STATE = os.path.expanduser("~/.mffd-bridge-replay-state.db")
DATASET_ROOT_MARKER = "4-Brückenschweißen"  # provenance paths are dataset-relative from here

# ── small thread-safe sqlite state store ───────────────────────────────────────
class State:
    """Resumable progress store. Maps logical keys → created appIds/ids.

    Tables:
      dos(do_id INTEGER PK, do_appid TEXT, do_numeric_id INTEGER, annotated INTEGER)
      struct_refs(do_id INT, ref_name TEXT, file TEXT, container_id INTEGER,
                  oid TEXT, ref_id INTEGER, annotated INTEGER, PK(do_id,ref_name,file))
      file_refs(ref_id INTEGER PK, ref_appid TEXT, uploaded INTEGER, annotated INTEGER)
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
              ref_id INTEGER PRIMARY KEY, ref_appid TEXT,
              uploaded INTEGER DEFAULT 0, annotated INTEGER DEFAULT 0);
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

    # -- file refs
    def get_file(self, ref_id: int):
        with self._lock:
            return self._conn.execute(
                "SELECT ref_appid, uploaded, annotated FROM file_refs WHERE ref_id=?", (ref_id,)
            ).fetchone()

    def put_file(self, ref_id, ref_appid, uploaded=0):
        with self._lock:
            self._conn.execute(
                "INSERT INTO file_refs(ref_id,ref_appid,uploaded) VALUES(?,?,?) "
                "ON CONFLICT(ref_id) DO UPDATE SET ref_appid=excluded.ref_appid, "
                "uploaded=MAX(file_refs.uploaded, excluded.uploaded)",
                (ref_id, ref_appid, uploaded),
            )
            self._conn.commit()

    def mark_file_uploaded(self, ref_id):
        with self._lock:
            self._conn.execute("UPDATE file_refs SET uploaded=1 WHERE ref_id=?", (ref_id,))
            self._conn.commit()

    def mark_file_annotated(self, ref_id):
        with self._lock:
            self._conn.execute("UPDATE file_refs SET annotated=1 WHERE ref_id=?", (ref_id,))
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


class Replayer:
    def __init__(self, client: Client, state: State, counts: Counts,
                 collection_appid: str, collection_numeric_id: int,
                 payload_root: str, dry_run: bool, verbose: bool):
        self.c = client
        self.st = state
        self.n = counts
        self.col_appid = collection_appid
        self.col_id = collection_numeric_id
        self.root = payload_root
        self.dry = dry_run
        self.verbose = verbose
        self._ann_kind_warned = False

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

    # -- annotations (best-effort secondary write; never aborts the import) ------
    def _annotate(self, subject_appid: str, subject_kind: str, prov_value: str) -> bool:
        if self.dry:
            return True
        body = {
            "subjectAppId": subject_appid,
            "subjectKind": subject_kind,
            "predicateIri": PROVENANCE_PREDICATE,
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

        # 1. DataObject (idempotent via state)
        cached = self.st.get_do(do_id)
        if cached and cached[0]:
            do_appid, do_numeric, annotated = cached
            self.n.add(dos_reused=1)
        else:
            # DataObject creation stays on the v2 surface (appId-native).
            created = self.c.post_json(
                f"/v2/collections/{self.col_appid}/data-objects",
                {"name": do_name},
            )
            do_appid = created["appId"]
            annotated = 0
            self.n.add(dos_created=1)
            # The v2 DO IO suppresses the numeric id; resolve it via the frozen v1
            # collection list (needed only for the v1 structured-data ref create,
            # which has no v2 counterpart). Cached in state so re-runs skip it.
            do_numeric = self._resolve_do_numeric(do_appid, do_name)
            self.st.put_do(do_id, do_appid, do_numeric)

        # provenance annotation on the DataObject (dataset-relative dir of the DO)
        if not annotated:
            prov = f"{DATASET_ROOT_MARKER}/bridgewelding/{do_name}"
            self._annotate(do_appid, "DataObject", prov)
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

        abspath = self._abs(relfile)
        if not os.path.exists(abspath):
            raise FileNotFoundError(f"structured payload missing on disk: {abspath}")

        # v1-only flow: no v2 structured-data reference/payload-upload endpoint
        # exists yet (the unified /v2/references surface has no structured-data
        # kind handler, and the structured-data payload POST is v1-only).
        # Documented gap — see report + aidocs/16 PLUGIN-V2 rows.
        if container_id is None:
            sdc = self.c.post_json(
                "/shepard/api/structuredDataContainers",
                {"name": f"{do_appid[:8]}-{ref_name}"},
            )
            container_id = sdc["id"]
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

        cached = self.st.get_file(ref_id)
        ref_appid = None
        uploaded = annotated = 0
        if cached:
            ref_appid, uploaded, annotated = cached

        size = os.path.getsize(abspath)

        # Single-file → singleton FileReference (FR1b): multipart POST /v2/files
        # creates the reference AND uploads the bytes in one call (the unified
        # /v2/references?kind=file path rejects binary kinds by design). This is
        # the project-preferred shape — no pointless bundle/selection layer.
        if not ref_appid or not uploaded:
            filename = os.path.basename(relfile)
            with open(abspath, "rb") as fh:
                resp = self.c.request(
                    "POST",
                    f"/v2/files?name={requests.utils.quote(ref_name)}"
                    f"&parentDataObjectAppId={do_appid}",
                    files={"file": (filename, fh, "application/octet-stream")},
                    expect=(200, 201),
                    retry_on_403=True,  # tolerate :Permissions wiring lag post-DO-create
                )
            created = resp.json()
            ref_appid = created["appId"]
            self.st.put_file(ref_id, ref_appid, uploaded=1)
            self.n.add(bytes_uploaded=size)

        self.n.add(file_refs=1)

        # 3. provenance annotation on the file reference
        if not annotated:
            self._annotate(ref_appid, "Reference", self._prov_path(relfile))
            self.st.mark_file_annotated(ref_id)

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
    rep = Replayer(client, state, counts, args.collection_appid, col_numeric,
                   payload_root, args.dry_run, args.verbose)

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
