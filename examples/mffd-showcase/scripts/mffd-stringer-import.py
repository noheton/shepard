#!/usr/bin/env python3
"""mffd-stringer-import.py — import the MFFD "stringer welding" wave (W8b) into a
live Shepard instance from LOCAL data only.

Context
-------
The stringer-welding step is the continuous-ultrasonic-seam counterpart to the
spot-welding step (which is ultrasonic *spot* mode). The source corpus is the
extracted ``Stringer_schweissungen.zip`` (231 GB zip → ~307 GB on disk). Unlike
the cube3 ``ts-export`` corpora (per-DO ``metadata.json`` + ``files/`` + ``ts/``),
this is a **flat operator dump**: a single top-level directory holding, per weld
run, one frame directory (``P##…/`` of ~5–6 k ``.tiff`` IR frames) plus sibling
files sharing the run's base name:

    Stringer_schweissungen/
      P01_1teBahn/                 ← 5 489 .tiff frames (the IR camera bundle)
      P01_1teBahn.svdx             ← TwinCAT Scope welding SSOT (sibling)
      P01_1teBahn.txt              ← raw scope export (binary; sibling)
      P01_1.Bahn.MP4              ← GoPro process video (sibling, loose)
      P01_1.Bahn_Fehler.MP4       ← defect-tagged video (NCR signal)
      …
      Energie/                     ← energy-analysis outputs (programme-level)
      LRV_Videos/                  ← 158 GoPro low-res previews (programme-level)
      Sortieren_pruefen/           ← 8 unassigned "to-check" svdx (programme-level)
      2023-03-29-Convertermessungen.xlsx  ← ultrasonic converter measurements

So the import model is:

  * **1 run DataObject per top-level ``P##…`` directory** (134 of them). The dir
    name (normalised) is the stable source id →
    ``urn:shepard:source:stringer-id = <dirname>``. This is the idempotency key.
  * Each run DO carries:
      - the dir's ``.tiff`` frames as **ONE FileBundleReference (FR1a)** — an IR
        image series of ~5–6 k frames per run is exactly the multi-file shape the
        CLAUDE.md "Reserve FileBundleReference for genuinely multi-file bundles —
        image series, mesh sets" rule prescribes. The whole series is one bundle
        named for the run, with every ``.tiff`` (sorted by filename so frame order
        is preserved) uploaded into the bundle's default FileGroup. This replaces
        the previous one-singleton-per-frame shape (~712 k singleton refs across
        134 runs → 134 frame bundles). ``--max-tiffs-per-run`` caps the frame
        count for a slice test.
      - the sibling ``<run>.svdx`` (TwinCAT scope SSOT) as a singleton FileReference.
      - the sibling ``<run>.txt`` raw-scope export as a singleton FileReference.
      - the sibling ``<run>*.MP4`` / ``.LRV`` videos (matched by run-base prefix)
        as singleton FileReferences.
  * Facets parsed from the dir name become annotations on the DO:
      - ``urn:shepard:mffd:stringer-position`` (P01–P18, normalised)
      - ``urn:shepard:mffd:weld-pass`` ∈ {1, 2}            (1teBahn / 2teBahn)
      - ``urn:shepard:mffd:stringer-variant`` ∈ {plain, prime}  (Strich = prime)
      - ``urn:shepard:mffd:stringer-side`` = true|false   (``_S_`` side variant)
      - ``urn:shepard:status:defect-run`` = true          (``_Fehler`` dirs → NCR)
      - ``urn:shepard:mffd:weld-step`` = stringer-welding
      - ``urn:shepard:mffd:weld-subtype`` = ultrasonic
      - ``urn:shepard:mffd:weld-mode`` = continuous
      - ``urn:shepard:source:provenance`` = <dataset-relative path>
  * **Lineage**: within a (position, variant, side) group, pass-1 → pass-2 is a
    Predecessor edge (1teBahn precedes 2teBahn). The v1
    ``/dataObjects/{id}/predecessors`` surface carries it (no v2 predecessor
    endpoint — documented inline).

This is NOT the cube3 metadata.json shape. There is no manifest.json inside the
tree (none found); ``stringer-extract.log`` is empty (0 bytes). The structure was
characterised directly from disk; see the report.

Target collection — seeded first
---------------------------------
``mffd-stringer-welding`` does NOT exist yet. ``--seed`` (default on) creates it
as a sub-collection of the MFFD Project (appId
``019ed455-62cd-75b5-951e-b837ffdace16``) mirroring seed-mffd-collections.py
(same v1 create + ``urn:shepard:partOf`` annotation). Idempotent: reused if found.

Ingest surface (v2 + appId, per CLAUDE.md; v1 only where no v2 exists)
----------------------------------------------------------------------
  1. Collection create   : ``POST /shepard/api/collections`` (v1 — the only create
                           surface; same as seed-mffd-collections.py). The new
                           collection is addressed by appId thereafter.
  2. partOf annotation   : ``POST /v2/annotations``.
  3. DataObject create   : ``POST /shepard/api/collections/{colNum}/dataObjects``
                           → {id, appId}. v1 here (not v2) because the v2 DO-create
                           response carries no numeric ``id``, and the v1
                           FileBundleReference create (step 4a) needs the numeric
                           collection + DO ids. The DO is addressed by appId for all
                           annotation / predecessor work thereafter.
  3b. Frame bundle (FR1a): the ``.tiff`` IR image series → ONE FileBundleReference.
        a) ``POST /shepard/api/fileContainers`` {name} → {id, appId} — one
           FileContainer per run to back the bundle's bytes.
        b) ``POST /shepard/api/collections/{colNum}/dataObjects/{doNum}/fileReferences``
           {name, fileContainerId, fileOids:[<placeholder-uuid>]} → the v1
           FileReference create == this fork's FileBundleReference. ``fileOids`` is
           ``@NotEmpty`` server-side, but a non-existent oid is logged-and-ignored
           by ``FileBundleReferenceService.createReference`` (it only attaches oids
           that resolve), so a throwaway UUID satisfies the validator while the
           bundle is born with an empty ``default`` FileGroup. (The direct S3
           container-payload upload — ``POST /fileContainers/{id}/payload`` — is 503
           "not supported for provider s3"; the group-upload below is the only
           S3-correct ingest path, and it needs an existing bundle, hence the
           placeholder-oid bootstrap.)
        c) For each frame, ``POST /v2/bundles/{bundleAppId}/groups/{groupAppId}/files``
           multipart ``file=…`` → server stores the bytes (GridFS-backed; returns a
           ShepardFile ``{oid, filename, fileSize, md5}``) and links it to the group.
           Verify per page via ``GET /v2/bundles/{bundleAppId}/groups/{groupAppId}/files``
           (PagedFilesIO ``totalElements``).
  4. Singleton file      : the svdx/txt/MP4 siblings stay singleton FileReferences.
                           TWO-STEP (the old multipart ``POST /v2/files`` is RETIRED
                           — 410 Gone, APISIMP-KIND-DISCRIMINATOR-2, confirmed live
                           2026-06-18):
                             a) ``POST /v2/references?kind=file&dataObjectAppId=…``
                                JSON {name} → {appId}
                             b) ``PUT /v2/references/{appId}/content?filename=…``
                                octet-stream body → uploads the bytes (FR1b singleton).
                           List: ``GET /v2/references?kind=file&dataObjectAppId=…``
                           (the old ``/v2/files/by-data-object`` is also retired).
                           Content read-back: ``GET /v2/references/{appId}/content``.
  5. DO annotations      : ``POST /v2/annotations`` (subjectKind=DataObject; Reference
                           annotations 403, so annotate the DO only).
  6. Predecessor edge    : ``PUT /shepard/api/dataObjects/{numId}/predecessors``
                           (v1 — no v2 predecessor endpoint yet).
  7. Permissions         : ``PUT /shepard/api/collections/{numId}/permissions``
                           {permissionType: PublicReadable} — IMPORT-PERMS-1. There is
                           NO v2 collection/file permissions endpoint; references inherit
                           the parent collection's permission, so one collection-level
                           PUT makes every imported file readable to demo users.

Idempotency + resumability
--------------------------
  * sqlite state (``--state``): per run dir → (do_appid, status); per uploaded
    file → ref appId. A re-run skips finished work.
  * Idempotent against LIVE Shepard: a startup LiveIndex pulls every existing
    ``urn:shepard:source:stringer-id`` annotation in the target collection AND
    the existing file-ref names per DO, so a wiped state file re-converges without
    duplicate DOs / files.
  * Completeness: retry-forever on transport/5xx; short-upload re-upload loop
    (verify stored content length == source). A run is never silently skipped.

CLI
---
    mffd-stringer-import.py --dry-run
    mffd-stringer-import.py --limit 2 --max-tiffs-per-run 5 --verbose   # slice
    mffd-stringer-import.py --workers 6                                  # FULL run

Reference implementations reused (CLAUDE.md "reuse before reimplement"):
  * mffd-tapelaying-timeseries-import.py — Client (retry-forever + bounded-403),
    Counts, State, disk floor, load_api_key.
  * mffd-thermography-import.py — singleton /v2/files upload + short-upload verify,
    LiveIndex idempotency, the source-id annotation contract, DO-level annotate.
  * seed-mffd-collections.py — the v1 collection-create + partOf-annotation seed.
"""
from __future__ import annotations

import argparse
import os
import re
import shutil
import sqlite3
import sys
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Optional
from urllib.parse import quote

try:
    import requests
except ImportError:
    sys.stderr.write("ERROR: this script needs `requests`.  pip install requests\n")
    sys.exit(2)

# ── defaults ────────────────────────────────────────────────────────────────
DEFAULT_HOST = "https://shepard-api.nuclide.systems"
DEFAULT_SOURCE = ("/mnt/pve/unas/dump/dataset/Stringer_schweissungen-extracted/"
                  "Stringer_schweissungen")
DEFAULT_KEYFILE = "/root/.claude/uploads/mffd-import-key-2026-06-17.txt"
DEFAULT_STATE = str(Path(__file__).resolve().parent / ".mffd-stringer-import-state.sqlite")

PROJECT_APPID = "019ed455-62cd-75b5-951e-b837ffdace16"
COLLECTION_NAME = "mffd-stringer-welding"
COLLECTION_DESC = ("Stringer welding process step (W8b) — continuous ultrasonic "
                   "seam welding. Parallel to spot/bridge welding. Per-run "
                   "DataObjects (P01-P18 × pass × variant × side) carrying IR "
                   "frame bundles + TwinCAT scope (.svdx/.txt) + GoPro video.")

# predicates
PRED_PART_OF = "urn:shepard:partOf"
PRED_SOURCE_ID = "urn:shepard:source:stringer-id"
PRED_PROVENANCE = "urn:shepard:source:provenance"
PRED_WELD_STEP = "urn:shepard:mffd:weld-step"
PRED_WELD_SUBTYPE = "urn:shepard:mffd:weld-subtype"
PRED_WELD_MODE = "urn:shepard:mffd:weld-mode"
PRED_POSITION = "urn:shepard:mffd:stringer-position"
PRED_PASS = "urn:shepard:mffd:weld-pass"
PRED_VARIANT = "urn:shepard:mffd:stringer-variant"
PRED_SIDE = "urn:shepard:mffd:stringer-side"
PRED_DEFECT = "urn:shepard:status:defect-run"

DATASET_ROOT = "/mnt/pve/unas/dump/dataset"
SHEPARD_ROOT = "/opt/shepard"
DEFAULT_DISK_FLOOR_GB = 50.0

VIDEO_EXTS = {".mp4", ".lrv", ".avi"}
SCOPE_EXTS = {".svdx", ".txt"}


# ── HTTP client (ported from mffd-tapelaying-timeseries-import.py) ────────────
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
    runs_done: int = 0
    runs_reused: int = 0
    dos_created: int = 0
    dos_reused: int = 0
    file_refs: int = 0
    file_refs_reused: int = 0
    bundles_created: int = 0
    bundles_reused: int = 0
    frames_uploaded: int = 0
    frames_reused: int = 0
    tiffs_uploaded: int = 0
    scope_uploaded: int = 0
    videos_uploaded: int = 0
    bytes_uploaded: int = 0
    annotations: int = 0
    annotation_failures: int = 0
    predecessors: int = 0
    short_uploads: int = 0
    perms_set: int = 0
    errors: int = 0
    _lock: threading.Lock = field(default_factory=threading.Lock, repr=False)

    def add(self, **kw):
        with self._lock:
            for k, v in kw.items():
                setattr(self, k, getattr(self, k) + v)


# ── sqlite resumable state ─────────────────────────────────────────────────────
class State:
    def __init__(self, path: str):
        self._lock = threading.Lock()
        self.conn = sqlite3.connect(path, check_same_thread=False)
        self.conn.execute(
            "CREATE TABLE IF NOT EXISTS runs ("
            "  source_id TEXT PRIMARY KEY, do_appid TEXT, do_num INTEGER,"
            "  status TEXT, ts REAL)")
        self.conn.execute(
            "CREATE TABLE IF NOT EXISTS files ("
            "  do_appid TEXT, fname TEXT, ref_appid TEXT, ts REAL,"
            "  PRIMARY KEY (do_appid, fname))")
        # FR1a image-series bundle: one FileBundleReference per run holding ALL
        # that run's .tiff frames. `bundles` tracks the bundle + its backing
        # FileContainer + default group so a wiped/partial run re-converges; the
        # `frames` table records each uploaded frame's ShepardFile oid so a
        # resumed run skips frames already in the bundle.
        self.conn.execute(
            "CREATE TABLE IF NOT EXISTS bundles ("
            "  do_appid TEXT PRIMARY KEY, bundle_appid TEXT, group_appid TEXT,"
            "  fc_num INTEGER, fc_appid TEXT, ts REAL)")
        self.conn.execute(
            "CREATE TABLE IF NOT EXISTS frames ("
            "  bundle_appid TEXT, fname TEXT, oid TEXT, ts REAL,"
            "  PRIMARY KEY (bundle_appid, fname))")
        # forward-compat: add do_num column if upgrading an older state file
        try:
            self.conn.execute("ALTER TABLE runs ADD COLUMN do_num INTEGER")
        except sqlite3.OperationalError:
            pass
        self.conn.commit()

    def get_run(self, source_id: str):
        with self._lock:
            row = self.conn.execute(
                "SELECT do_appid, status, do_num FROM runs WHERE source_id=?",
                (source_id,)).fetchone()
        return row

    def put_run(self, source_id: str, do_appid: str, status: str,
                do_num: Optional[int] = None):
        with self._lock:
            self.conn.execute(
                "INSERT INTO runs(source_id,do_appid,do_num,status,ts) "
                "VALUES(?,?,?,?,?) "
                "ON CONFLICT(source_id) DO UPDATE SET do_appid=excluded.do_appid,"
                "do_num=COALESCE(excluded.do_num, runs.do_num),"
                "status=excluded.status, ts=excluded.ts",
                (source_id, do_appid, do_num, status, time.time()))
            self.conn.commit()

    # ── bundle state (FR1a image-series) ─────────────────────────────────────
    def get_bundle(self, do_appid: str):
        with self._lock:
            return self.conn.execute(
                "SELECT bundle_appid, group_appid, fc_num, fc_appid "
                "FROM bundles WHERE do_appid=?", (do_appid,)).fetchone()

    def put_bundle(self, do_appid: str, bundle_appid: str, group_appid: str,
                   fc_num: int, fc_appid: str):
        with self._lock:
            self.conn.execute(
                "INSERT INTO bundles(do_appid,bundle_appid,group_appid,fc_num,"
                "fc_appid,ts) VALUES(?,?,?,?,?,?) "
                "ON CONFLICT(do_appid) DO UPDATE SET bundle_appid=excluded.bundle_appid,"
                "group_appid=excluded.group_appid, fc_num=excluded.fc_num,"
                "fc_appid=excluded.fc_appid, ts=excluded.ts",
                (do_appid, bundle_appid, group_appid, fc_num, fc_appid, time.time()))
            self.conn.commit()

    def get_frame(self, bundle_appid: str, fname: str) -> Optional[str]:
        with self._lock:
            row = self.conn.execute(
                "SELECT oid FROM frames WHERE bundle_appid=? AND fname=?",
                (bundle_appid, fname)).fetchone()
        return row[0] if row else None

    def put_frame(self, bundle_appid: str, fname: str, oid: str):
        with self._lock:
            self.conn.execute(
                "INSERT INTO frames(bundle_appid,fname,oid,ts) VALUES(?,?,?,?) "
                "ON CONFLICT(bundle_appid,fname) DO UPDATE SET oid=excluded.oid,"
                "ts=excluded.ts",
                (bundle_appid, fname, oid, time.time()))
            self.conn.commit()

    def get_file(self, do_appid: str, fname: str) -> Optional[str]:
        with self._lock:
            row = self.conn.execute(
                "SELECT ref_appid FROM files WHERE do_appid=? AND fname=?",
                (do_appid, fname)).fetchone()
        return row[0] if row else None

    def put_file(self, do_appid: str, fname: str, ref_appid: str):
        with self._lock:
            self.conn.execute(
                "INSERT INTO files(do_appid,fname,ref_appid,ts) VALUES(?,?,?,?) "
                "ON CONFLICT(do_appid,fname) DO UPDATE SET ref_appid=excluded.ref_appid,"
                "ts=excluded.ts",
                (do_appid, fname, ref_appid, time.time()))
            self.conn.commit()


# ── source model ─────────────────────────────────────────────────────────────
RUN_RE = re.compile(r"^[pP]\s*0*(\d{1,2})", re.I)
SPECIAL_DIRS = {".vs", "Energie", "LRV_Videos", "Sortieren_pruefen", "Videos",
                "Stringer_schweissungen"}


@dataclass
class RunDir:
    source_id: str          # normalised dir name (idempotency key)
    raw_name: str           # on-disk dir name
    dirpath: Path
    position: str           # P01..P18
    pass_no: Optional[str]  # "1" | "2" | None
    variant: str            # "plain" | "prime"
    side: bool              # True if _S_ side variant
    defect: bool            # True if _Fehler
    tiffs: list[Path] = field(default_factory=list)
    siblings: list[Path] = field(default_factory=list)  # svdx/txt/video sharing base


def _parse_facets(name: str) -> dict:
    low = name.lower()
    m = RUN_RE.match(name)
    position = f"P{int(m.group(1)):02d}" if m else "P00"
    pass_no = "1" if "1tebahn" in low.replace("_", "") or "1te_bahn" in low else (
        "2" if "2tebahn" in low.replace("_", "") or "2te_bahn" in low else None)
    variant = "prime" if "strich" in low else "plain"
    # side variant: an _S_ / _s_ token (not the trailing 'strich')
    side = bool(re.search(r"_s_", low) or re.search(r"_s$", low) or "_s_" in low)
    defect = "fehler" in low
    return {"position": position, "pass_no": pass_no, "variant": variant,
            "side": side, "defect": defect}


def _run_base(name: str) -> str:
    """Normalise a dir/file name to a run-base key for matching sibling
    videos/scope to a run dir. The dir form ``P01_1teBahn`` and the loose-video
    form ``P01_1.Bahn.MP4`` must collapse to the SAME key. Approach: drop the
    extension + known suffixes, then collapse the pass token (``1teBahn`` /
    ``1.Bahn`` / ``1Bahn`` → ``pass1``) and strip separators.
    e.g. 'P01_1.Bahn.MP4' and 'P01_1teBahn' -> 'p01pass1'."""
    stem = re.sub(r"\.(mp4|lrv|avi|svdx|txt)$", "", name, flags=re.I)
    stem = re.sub(r"_converted$", "", stem, flags=re.I)
    stem = re.sub(r"_zusatz\d+$", "", stem, flags=re.I)
    low = stem.lower()
    # collapse "<n>(te)?_?\.?bahn" → "pass<n>"
    low = re.sub(r"(\d)\s*(te)?[._]?\s*bahn", r"pass\1", low)
    # normalise zero-pad on the leading position (p1 / p01 → p01)
    m = RUN_RE.match(low)
    if m:
        low = RUN_RE.sub(f"p{int(m.group(1)):02d}", low, count=1)
    return re.sub(r"[._\s]", "", low)


def discover_runs(root: Path, limit: Optional[int],
                  max_tiffs: Optional[int]) -> tuple[list[RunDir], dict]:
    """Build the run model. Returns (runs, programme_extras)."""
    entries = sorted(root.iterdir(), key=lambda p: p.name)
    run_dirs = [e for e in entries if e.is_dir() and e.name not in SPECIAL_DIRS
                and RUN_RE.match(e.name)]
    loose_files = [e for e in entries if e.is_file()]

    # index loose files by a normalised run-base so we can attach siblings.
    base_index: dict[str, list[Path]] = {}
    for f in loose_files:
        base_index.setdefault(_run_base(f.name), []).append(f)

    runs: list[RunDir] = []
    for d in run_dirs:
        facets = _parse_facets(d.name)
        tiffs = sorted(
            (p for p in d.iterdir()
             if p.is_file() and p.suffix.lower() in (".tiff", ".tif")),
            key=lambda p: p.name)
        if max_tiffs is not None:
            tiffs = tiffs[:max_tiffs]
        # siblings: loose files whose run-base matches this dir's run-base
        dbase = _run_base(d.name)
        siblings = list(base_index.get(dbase, []))
        runs.append(RunDir(
            source_id=d.name.strip(),
            raw_name=d.name,
            dirpath=d,
            position=facets["position"],
            pass_no=facets["pass_no"],
            variant=facets["variant"],
            side=facets["side"],
            defect=facets["defect"],
            tiffs=tiffs,
            siblings=siblings,
        ))
        if limit is not None and len(runs) >= limit:
            break

    extras = {
        "loose_files": len(loose_files),
        "special_dirs": [e.name for e in entries
                         if e.is_dir() and e.name in SPECIAL_DIRS],
    }
    return runs, extras


# ── LiveIndex (idempotency against live Shepard) ──────────────────────────────
class LiveIndex:
    """source-id -> DO appId, from existing annotations in the target collection."""

    def __init__(self):
        self.by_source_id: dict[str, str] = {}

    def find_do(self, source_id: str) -> Optional[str]:
        return self.by_source_id.get(source_id)


def build_live_index(client: Client, verbose: bool) -> LiveIndex:
    idx = LiveIndex()
    page = 0
    while True:
        try:
            batch = client.get_json(
                f"/v2/annotations?predicateIri={quote(PRED_SOURCE_ID)}"
                f"&page={page}&pageSize=200")
        except RuntimeError:
            break
        if not batch:
            break
        for a in batch:
            lit = a.get("objectLiteral")
            subj = a.get("subjectAppId")
            if lit and subj:
                idx.by_source_id[lit] = subj
        if len(batch) < 200:
            break
        page += 1
    if verbose:
        print(f"live index    : {len(idx.by_source_id)} existing stringer-id DOs",
              file=sys.stderr, flush=True)
    return idx


# ── importer ────────────────────────────────────────────────────────────────
class Importer:
    def __init__(self, client: Client, state: State, counts: Counts,
                 col_appid: str, col_numeric: int, live: LiveIndex, verbose: bool,
                 frame_workers: int = 8):
        self.c = client
        self.st = state
        self.n = counts
        self.col = col_appid
        self.col_num = col_numeric
        self.live = live
        self.verbose = verbose
        self.frame_workers = max(1, frame_workers)
        self._lock = threading.Lock()
        self._live_file_refs: dict[str, set[str]] = {}
        self._do_num_idx: Optional[dict[str, int]] = None
        self._live_frame_names: dict[str, set[str]] = {}
        self._frame_set_locks: dict[str, threading.Lock] = {}

    # ── annotations
    def _annotate(self, do_appid: str, predicate: str, value: str) -> bool:
        body = {"subjectAppId": do_appid, "subjectKind": "DataObject",
                "predicateIri": predicate, "objectLiteral": value,
                "sourceMode": "ai"}
        # idempotent: skip if already present
        try:
            existing = self.c.get_json(
                f"/v2/annotations?subjectAppId={do_appid}&subjectKind=DataObject"
                f"&predicateIri={quote(predicate)}&pageSize=200")
            for a in (existing or []):
                if (a.get("objectLiteral") == value):
                    return True
        except RuntimeError:
            pass
        try:
            self.c.post_json("/v2/annotations", body, retry_on_403=True)
            self.n.add(annotations=1)
            return True
        except RuntimeError as e:
            self.n.add(annotation_failures=1)
            if self.verbose:
                print(f"  [annotation WARN] {do_appid} {predicate}: {e}",
                      file=sys.stderr, flush=True)
            return False

    # ── existing file refs on a DO
    def _existing_file_refs(self, do_appid: str, refresh=False) -> set:
        if not refresh:
            with self._lock:
                cached = self._live_file_refs.get(do_appid)
            if cached is not None:
                return cached
        names: set = set()
        try:
            lst = self.c.get_json(
                f"/v2/references?kind=file&dataObjectAppId={do_appid}")
            if isinstance(lst, list):
                names = {x.get("name") for x in lst if x.get("name")}
        except RuntimeError:
            pass
        with self._lock:
            merged = self._live_file_refs.get(do_appid, set()) | names
            self._live_file_refs[do_appid] = merged
            return merged

    def _content_length(self, ref_appid: str) -> int:
        """Stored byte length of a FileReference's content. Read from the
        reference METADATA (``payload.file.fileSize``) rather than streaming the
        content endpoint — the content GET is proxy-gated (405 behind Caddy in
        this deployment) and range semantics make it unreliable for a pure size
        probe. The metadata fileSize is the authoritative stored length. -1 on
        failure so the caller treats it as a mismatch and re-uploads."""
        try:
            meta = self.c.get_json(f"/v2/references/{ref_appid}")
            f = (meta or {}).get("payload", {}).get("file")
            if f and f.get("fileSize") is not None:
                return int(f["fileSize"])
            return -1
        except (RuntimeError, ValueError, KeyError, TypeError):
            return -1

    def _upload_file(self, do_appid: str, path: Path) -> Optional[str]:
        """Singleton FileReference (FR1b). Returns ref appId. Idempotent + verifies
        stored length == source (re-upload on short-upload)."""
        fname = path.name
        cached = self.st.get_file(do_appid, fname)
        if cached:
            self.n.add(file_refs=1, file_refs_reused=1)
            return cached
        if fname in self._existing_file_refs(do_appid):
            self.n.add(file_refs=1, file_refs_reused=1)
            return None
        if fname in self._existing_file_refs(do_appid, refresh=True):
            self.n.add(file_refs=1, file_refs_reused=1)
            return None
        size = path.stat().st_size
        # Backend hard cap: the file-content PUT rejects >2 GiB with a 400
        # ("File exceeds 2147483648 bytes"). Some GoPro .MP4 siblings exceed it.
        # A 400 is not transient, so skip-and-record (don't retry-forever or
        # fail the whole run). Tracked in aidocs/16 as IMPORT-FILESIZE-2GB.
        if size > 2_147_483_648:
            self._log(f"  [skip >2GiB] {fname} ({size/1e9:.1f} GB) exceeds the "
                      f"backend 2 GiB content cap — deferred (IMPORT-FILESIZE-2GB)")
            return None
        attempt = 0
        while True:
            attempt += 1
            # step a) create the file reference (JSON), step b) PUT the bytes.
            # The old multipart POST /v2/files is RETIRED (410, APISIMP-KIND-
            # DISCRIMINATOR-2). retry_on_403 tolerates :Permissions wiring lag
            # right after the DO create.
            created = self.c.post_json(
                f"/v2/references?kind=file&dataObjectAppId={do_appid}",
                {"name": fname}, retry_on_403=True)
            ref_appid = created["appId"]
            with open(path, "rb") as fh:
                self.c.request(
                    "PUT",
                    f"/v2/references/{ref_appid}/content?filename={quote(fname)}",
                    data=fh, headers={"Content-Type": "application/octet-stream"},
                    expect=(200, 201, 204), retry_on_403=True)
            stored = self._content_length(ref_appid)
            if stored == size or size == 0:
                break
            self.n.add(short_uploads=1)
            if self.verbose:
                print(f"  [short-upload] {fname}: {stored}/{size} (attempt {attempt})"
                      f" — re-uploading", file=sys.stderr, flush=True)
            try:
                self.c.request("DELETE", f"/v2/references/{ref_appid}",
                               expect=(200, 204), retry_on_403=True)
            except RuntimeError:
                pass
        self.st.put_file(do_appid, fname, ref_appid)
        with self._lock:
            self._live_file_refs.setdefault(do_appid, set()).add(fname)
        # Note: references inherit the parent collection's permission. PublicReadable
        # is set ONCE at the collection level (IMPORT-PERMS-1) — there is no per-file
        # v2 permissions endpoint, so we do not PATCH each reference.
        self.n.add(file_refs=1, bytes_uploaded=size)
        ext = path.suffix.lower()
        if ext in (".tiff", ".tif"):
            self.n.add(tiffs_uploaded=1)
        elif ext in SCOPE_EXTS:
            self.n.add(scope_uploaded=1)
        elif ext in VIDEO_EXTS:
            self.n.add(videos_uploaded=1)
        return ref_appid

    # ── numeric DO id resolution (for v1 FileBundleReference create) ─────────
    def _build_do_numeric_index(self):
        """One pass over the collection's v1 DataObjects → {appId: numericId}.
        Built lazily on first need; the v2 DO-create response has no numeric id,
        so a DO reused from the live index (or a wiped state file) needs this."""
        with self._lock:
            if getattr(self, "_do_num_idx", None) is not None:
                return
            idx: dict[str, int] = {}
            page = 0
            while True:
                try:
                    rows = self.c.get_json(
                        f"/shepard/api/collections/{self.col_num}/dataObjects"
                        f"?page={page}&size=200")
                except RuntimeError:
                    break
                if not rows:
                    break
                for d in rows:
                    if d.get("appId") and d.get("id") is not None:
                        idx[d["appId"]] = d["id"]
                if len(rows) < 200:
                    break
                page += 1
            self._do_num_idx = idx

    def _resolve_do_numeric(self, do_appid: str) -> Optional[int]:
        self._build_do_numeric_index()
        return self._do_num_idx.get(do_appid)

    # ── frame bundle (FR1a image series) ─────────────────────────────────────
    def _bundle_frame_count(self, bundle_appid: str, group_appid: str) -> int:
        """Authoritative live count of frames in the bundle's default group, via
        the paginated PagedFilesIO envelope (cheap — page 0, size 1)."""
        try:
            env = self.c.get_json(
                f"/v2/bundles/{bundle_appid}/groups/{group_appid}/files"
                f"?page=0&pageSize=1")
            for k in ("totalElements", "total"):
                if env.get(k) is not None:
                    return int(env[k])
        except (RuntimeError, ValueError, TypeError):
            pass
        return -1

    def _ensure_bundle(self, do_appid: str, do_num: int, run: RunDir):
        """Find-or-create the run's FileBundleReference (FR1a) + backing
        FileContainer + default group. Idempotent: reuses the bundle recorded in
        sqlite, otherwise probes the live DO for an existing bundle of the same
        name before creating a new one. Returns (bundle_appid, group_appid,
        fc_num, fc_appid)."""
        cached = self.st.get_bundle(do_appid)
        if cached and all(cached[:4]):
            self.n.add(bundles_reused=1)
            return cached[0], cached[1], cached[2], cached[3]

        bundle_name = run.source_id
        # live probe (idempotency for a wiped/partial state file): a prior run may
        # have left a FileBundleReference on this DO. The `kind=bundle` discriminator
        # is NOT registered on the unified /v2/references surface in this deployment,
        # so list via the v1 fileReferences surface (== FileBundleReference) and
        # resolve the default group via /v2/bundles/{appId}/groups.
        try:
            existing = self.c.get_json(
                f"/shepard/api/collections/{self.col_num}/dataObjects/{do_num}/fileReferences")
            for ref in (existing or []):
                if ref.get("name") == bundle_name and ref.get("appId"):
                    b_appid = ref["appId"]
                    fc_num = ref.get("fileContainerId") or 0
                    groups = self.c.get_json(f"/v2/bundles/{b_appid}/groups")
                    if groups:
                        g_appid = groups[0]["appId"]
                        self.st.put_bundle(do_appid, b_appid, g_appid, fc_num, "")
                        self.n.add(bundles_reused=1)
                        return b_appid, g_appid, fc_num, ""
        except RuntimeError:
            pass

        # create a FileContainer (v1) to back the bundle's bytes
        fc = self.c.post_json("/shepard/api/fileContainers",
                              {"name": f"stringer-frames-{bundle_name}"},
                              retry_on_403=True)
        fc_num = fc["id"]
        fc_appid = fc.get("appId", "")
        # create the FileBundleReference (v1) with a throwaway oid (logged-and-
        # ignored server-side; satisfies the @NotEmpty fileOids validator). The
        # bundle is born with an empty `default` FileGroup.
        placeholder = str(uuid.uuid4())
        br = self.c.post_json(
            f"/shepard/api/collections/{self.col_num}/dataObjects/{do_num}/fileReferences",
            {"name": bundle_name, "fileContainerId": fc_num,
             "fileOids": [placeholder]}, retry_on_403=True)
        b_appid = br["appId"]
        bg = self.c.get_json(f"/v2/bundles/{b_appid}")
        groups = bg.get("groups") or []
        if not groups:
            raise RuntimeError(f"bundle {b_appid} has no default group")
        g_appid = groups[0]["appId"]
        # container PublicReadable (IMPORT-PERMS-1): the bundle's bytes live in
        # this per-run FileContainer; references inherit collection perms but the
        # container itself needs PublicReadable so the frames are demo-readable.
        try:
            cur = self.c.get_json(f"/shepard/api/fileContainers/{fc_num}/permissions")
            cur["permissionType"] = "PublicReadable"
            self.c.request("PUT", f"/shepard/api/fileContainers/{fc_num}/permissions",
                           json=cur, expect=(200, 201, 204), retry_on_403=True)
            self.n.add(perms_set=1)
        except RuntimeError as e:
            if self.verbose:
                print(f"  [perms WARN] container {fc_num}: {e}",
                      file=sys.stderr, flush=True)
        self.st.put_bundle(do_appid, b_appid, g_appid, fc_num, fc_appid)
        # fresh bundle starts with an empty group — seed the live-frame cache so
        # the first frame upload doesn't waste a paginated round-trip.
        with self._lock:
            self._live_frame_names[b_appid] = set()
        self.n.add(bundles_created=1)
        return b_appid, g_appid, fc_num, fc_appid

    def _live_frame_set(self, bundle_appid: str, group_appid: str) -> set:
        """Set of frame filenames already in the bundle's group, fetched EXACTLY
        once per bundle and cached. Guards idempotency when the sqlite state file
        is wiped/partial but the bundle already holds frames — the group-upload
        endpoint does NOT dedupe by name, so without this a re-run would duplicate
        every frame. The full paginated fetch runs under a per-bundle lock so the
        N concurrent frame-workers don't each paginate (and so the set is fully
        populated before any worker consults it)."""
        with self._lock:
            cached = self._live_frame_names.get(bundle_appid)
            if cached is not None:
                return cached
            lock = self._frame_set_locks.setdefault(bundle_appid, threading.Lock())
        with lock:
            # double-check after acquiring the per-bundle lock
            with self._lock:
                cached = self._live_frame_names.get(bundle_appid)
            if cached is not None:
                return cached
            names: set = set()
            page = 0
            while True:
                try:
                    env = self.c.get_json(
                        f"/v2/bundles/{bundle_appid}/groups/{group_appid}/files"
                        f"?page={page}&pageSize=1000")
                except RuntimeError:
                    break
                items = env.get("items") or []
                for it in items:
                    nm = it.get("filename") or it.get("name")
                    if nm:
                        names.add(nm)
                total_pages = env.get("totalPages")
                if not items or (total_pages is not None and page + 1 >= total_pages):
                    break
                page += 1
            with self._lock:
                self._live_frame_names[bundle_appid] = names
            return names

    def _upload_frame(self, bundle_appid: str, group_appid: str, path: Path) -> Optional[str]:
        """Upload one .tiff frame into the bundle's default FileGroup. Idempotent
        on sqlite (skip already-uploaded frames) and on the live group contents
        (re-run with a wiped state file skips frames already present). Returns the
        ShepardFile oid."""
        fname = path.name
        cached = self.st.get_frame(bundle_appid, fname)
        if cached:
            self.n.add(frames_reused=1)
            return cached
        if fname in self._live_frame_set(bundle_appid, group_appid):
            self.n.add(frames_reused=1)
            return None
        size = path.stat().st_size
        attempt = 0
        while True:
            attempt += 1
            with open(path, "rb") as fh:
                resp = self.c.request(
                    "POST",
                    f"/v2/bundles/{bundle_appid}/groups/{group_appid}/files",
                    files={"file": (fname, fh, "application/octet-stream")},
                    expect=(200, 201), retry_on_403=True)
            rec = resp.json()
            oid = rec.get("oid")
            stored = rec.get("fileSize")
            if oid and (stored == size or size == 0 or stored is None):
                break
            self.n.add(short_uploads=1)
            if self.verbose:
                print(f"  [short-frame] {fname}: {stored}/{size} (attempt {attempt})"
                      f" — re-uploading", file=sys.stderr, flush=True)
        self.st.put_frame(bundle_appid, fname, oid)
        with self._lock:
            self._live_frame_names.setdefault(bundle_appid, set()).add(fname)
        self.n.add(frames_uploaded=1, file_refs=1, tiffs_uploaded=1,
                   bytes_uploaded=size)
        return oid

    def process(self, run: RunDir):
        # 1. DataObject — sqlite, then live index, then create. v1 create so we
        #    capture the numeric id needed for the v1 FileBundleReference create.
        cached = self.st.get_run(run.source_id)
        if cached and cached[1] == "done":
            self.n.add(runs_reused=1)
            return cached[0]
        do_appid = cached[0] if cached else None
        do_num = cached[2] if cached and len(cached) > 2 else None
        if not do_appid:
            do_appid = self.live.find_do(run.source_id)
        if do_appid:
            self.n.add(dos_reused=1)
            if do_num is None:
                do_num = self._resolve_do_numeric(do_appid)
        else:
            created = self.c.post_json(
                f"/shepard/api/collections/{self.col_num}/dataObjects",
                {"name": run.source_id}, retry_on_403=True)
            do_appid = created["appId"]
            do_num = created["id"]
            self.n.add(dos_created=1)
            self.live.by_source_id[run.source_id] = do_appid
        self.st.put_run(run.source_id, do_appid, "creating", do_num=do_num)

        # 2. annotations (facets + idempotency key + provenance)
        relpath = os.path.relpath(str(run.dirpath), DATASET_ROOT)
        self._annotate(do_appid, PRED_SOURCE_ID, run.source_id)
        self._annotate(do_appid, PRED_PROVENANCE, relpath)
        self._annotate(do_appid, PRED_WELD_STEP, "stringer-welding")
        self._annotate(do_appid, PRED_WELD_SUBTYPE, "ultrasonic")
        self._annotate(do_appid, PRED_WELD_MODE, "continuous")
        self._annotate(do_appid, PRED_POSITION, run.position)
        self._annotate(do_appid, PRED_VARIANT, run.variant)
        self._annotate(do_appid, PRED_SIDE, "true" if run.side else "false")
        if run.pass_no:
            self._annotate(do_appid, PRED_PASS, run.pass_no)
        if run.defect:
            self._annotate(do_appid, PRED_DEFECT, "true")

        # 3. files: scope/video siblings stay singleton FileReferences (distinct
        #    payloads, not part of the image series).
        for sib in run.siblings:
            self._upload_file(do_appid, sib)

        # 3b. the .tiff IR frames → ONE FileBundleReference (FR1a image series).
        #     All frames (already sorted by filename in discover_runs) go into the
        #     bundle's default FileGroup.
        if run.tiffs:
            if do_num is None:
                do_num = self._resolve_do_numeric(do_appid)
            if do_num is None:
                raise RuntimeError(
                    f"cannot resolve numeric DO id for {run.source_id}; "
                    f"FileBundleReference create needs it")
            b_appid, g_appid, fc_num, fc_appid = self._ensure_bundle(
                do_appid, do_num, run)
            # Pre-warm the live frame-name set ONCE before fanning out, so a
            # resume-after-wiped-state run doesn't re-upload frames already in the
            # bundle (the group-upload endpoint does not dedupe by name). A freshly
            # created bundle has its cache seeded empty by _ensure_bundle, so this
            # is a no-op there.
            self._live_frame_set(b_appid, g_appid)
            # Frames within a run upload concurrently — the dominant throughput
            # lever (a run has thousands of frames; the per-frame POST is the hot
            # path). The bundle/group already exists, so the uploads are
            # independent. State writes + counts are lock-guarded.
            if self.frame_workers > 1 and len(run.tiffs) > 1:
                with ThreadPoolExecutor(max_workers=self.frame_workers) as fex:
                    list(fex.map(
                        lambda t: self._upload_frame(b_appid, g_appid, t),
                        run.tiffs))
            else:
                for tif in run.tiffs:
                    self._upload_frame(b_appid, g_appid, tif)

        self.st.put_run(run.source_id, do_appid, "done", do_num=do_num)
        self.n.add(runs_done=1)
        if self.verbose:
            print(f"  [done] {run.source_id}: {len(run.tiffs)} tiffs + "
                  f"{len(run.siblings)} siblings → DO {do_appid}",
                  file=sys.stderr, flush=True)
        return do_appid


# ── predecessor wiring (v2 — appId-only, RFC 7396 merge-patch) ────────────────
def wire_predecessors(client: Client, col_appid: str, runs: list[RunDir],
                      do_appids: dict[str, str], counts: Counts, verbose: bool):
    """Within a (position, variant, side) group, pass-1 precedes pass-2. Set via
    the v2 DataObject merge-patch (``predecessorAppIds``) — appId-only, no numeric
    resolution, no v1 surface. The patch REPLACES predecessorAppIds, so we read
    the current set first and union pass-1 in (idempotent)."""
    groups: dict[tuple, dict[str, RunDir]] = {}
    for r in runs:
        if r.pass_no:
            groups.setdefault((r.position, r.variant, r.side), {})[r.pass_no] = r
    for key, byp in groups.items():
        if "1" in byp and "2" in byp:
            a1 = do_appids.get(byp["1"].source_id)
            a2 = do_appids.get(byp["2"].source_id)
            if not (a1 and a2):
                continue
            try:
                cur = client.get_json(
                    f"/v2/collections/{col_appid}/data-objects/{a2}")
                existing = set(cur.get("predecessorAppIds") or [])
                if a1 in existing:
                    counts.add(predecessors=1)  # already wired
                    continue
                existing.add(a1)
                client.patch_json(
                    f"/v2/collections/{col_appid}/data-objects/{a2}",
                    {"predecessorAppIds": sorted(existing)}, retry_on_403=True)
                counts.add(predecessors=1)
            except RuntimeError as e:
                if verbose:
                    print(f"  [predecessor WARN] {key}: {e}",
                          file=sys.stderr, flush=True)


# ── collection seeding ─────────────────────────────────────────────────────────
def ensure_collection(client: Client, dry: bool, verbose: bool) -> tuple[str, int]:
    """Idempotent: find-or-create mffd-stringer-welding under the MFFD Project."""
    # find existing by name (v1 list)
    page, size = 0, 100
    while True:
        rows = client.get_json(f"/shepard/api/collections?page={page}&size={size}")
        if not rows:
            break
        for c in rows:
            if c.get("name") == COLLECTION_NAME:
                appid = c["appId"]
                num = c["id"]
                print(f"collection    : reuse {COLLECTION_NAME} "
                      f"(appId={appid}, id={num})")
                # re-assert partOf (idempotent)
                if not dry:
                    _ensure_part_of(client, appid)
                return appid, num
        if len(rows) < size:
            break
        page += 1
    if dry:
        print(f"collection    : would CREATE {COLLECTION_NAME} under Project "
              f"{PROJECT_APPID}")
        return "<dry-run-collection-appId>", 0
    created = client.post_json("/shepard/api/collections",
                               {"name": COLLECTION_NAME, "description": COLLECTION_DESC,
                                "attributes": {}})
    appid = created["appId"]
    num = created["id"]
    print(f"collection    : CREATED {COLLECTION_NAME} (appId={appid}, id={num})")
    time.sleep(0.5)
    _ensure_part_of(client, appid)
    return appid, num


def _ensure_part_of(client: Client, col_appid: str):
    existing = client.get_json(
        f"/v2/annotations?subjectAppId={col_appid}&subjectKind=Collection"
        f"&predicateIri={quote(PRED_PART_OF)}&pageSize=200")
    for a in (existing or []):
        if a.get("objectLiteral") == PROJECT_APPID:
            return
    client.post_json("/v2/annotations", {
        "subjectAppId": col_appid, "subjectKind": "Collection",
        "predicateIri": PRED_PART_OF, "objectLiteral": PROJECT_APPID,
        "sourceMode": "human"}, retry_on_403=True)


# ── dry-run ─────────────────────────────────────────────────────────────────
def free_gb(path: str) -> float:
    _, _, free = shutil.disk_usage(path)
    return free / 1e9


def run_dry_run(runs: list[RunDir], extras: dict):
    print("\n── DRY-RUN plan ──")
    print(f"  run DataObjects (P##…)   : {len(runs)}")
    total_tiffs = sum(len(r.tiffs) for r in runs)
    total_sibs = sum(len(r.siblings) for r in runs)
    frame_bundles = sum(1 for r in runs if r.tiffs)
    tiff_bytes = sum(t.stat().st_size for r in runs for t in r.tiffs)
    sib_bytes = sum(s.stat().st_size for r in runs for s in r.siblings)
    print(f"  frame FileBundleReferences: {frame_bundles:,} "
          f"(ONE per run; {total_tiffs:,} .tiff frames total inside them)")
    print(f"  sibling FileReferences    : {total_sibs:,} "
          f"(svdx/txt/video matched by run-base; kept as singletons)")
    print(f"  total reference nodes     : {frame_bundles + total_sibs:,} "
          f"(was {total_tiffs + total_sibs:,} pre-FR1a singleton-per-frame)")
    print(f"  total bytes (tiffs+sibs)  : {(tiff_bytes + sib_bytes) / 1e9:.2f} GB")
    defects = sum(1 for r in runs if r.defect)
    print(f"  defect runs (_Fehler→NCR): {defects}")
    positions = sorted({r.position for r in runs})
    print(f"  stringer positions       : {len(positions)} {positions}")
    print(f"  programme-level extras    : loose-files={extras['loose_files']}, "
          f"special-dirs={extras['special_dirs']}")
    print(f"  est. TS points           : 0 (no per-frame timeseries; .svdx is the "
          f"scope SSOT, ingested as a file — tier-2 svdx decode is a separate wave)")
    print(f"  disk free now            : {free_gb(SHEPARD_ROOT):.1f} GB")
    print("\n  sample runs:")
    for r in runs[:6]:
        print(f"    {r.source_id}: pos={r.position} pass={r.pass_no} "
              f"variant={r.variant} side={r.side} defect={r.defect} "
              f"tiffs={len(r.tiffs)} siblings={[s.name for s in r.siblings]}")


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


def main():
    ap = argparse.ArgumentParser(
        description="Import the MFFD stringer-welding wave (W8b) from LOCAL data.")
    ap.add_argument("--source", default=DEFAULT_SOURCE)
    ap.add_argument("--host", default=DEFAULT_HOST)
    ap.add_argument("--api-key", default=None)
    ap.add_argument("--keyfile", default=DEFAULT_KEYFILE)
    ap.add_argument("--state", default=DEFAULT_STATE)
    ap.add_argument("--workers", type=int, default=4,
                    help="run-level parallelism (DataObjects processed at once)")
    ap.add_argument("--frame-workers", type=int, default=8,
                    help="intra-run frame-upload parallelism (frames per run "
                         "uploaded concurrently into the bundle's group)")
    ap.add_argument("--limit", type=int, default=None, help="only first N run dirs")
    ap.add_argument("--max-tiffs-per-run", type=int, default=None,
                    help="cap tiff uploads per run (slice-test knob; default all)")
    ap.add_argument("--no-seed", action="store_true",
                    help="do not create the collection (assume it exists)")
    ap.add_argument("--disk-floor-gb", type=float, default=DEFAULT_DISK_FLOOR_GB)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--verbose", action="store_true")
    args = ap.parse_args()

    source_root = Path(args.source)
    api_key = load_api_key(args.api_key, args.keyfile)
    client = Client(args.host, api_key, verbose=args.verbose)

    print(f"source        : {source_root}")
    print(f"host          : {args.host}")
    print(f"mode          : {'DRY-RUN' if args.dry_run else 'LIVE'}  "
          f"workers={args.workers} frame-workers={args.frame_workers}"
          f"{'  limit='+str(args.limit) if args.limit else ''}"
          f"{'  max-tiffs='+str(args.max_tiffs_per_run) if args.max_tiffs_per_run else ''}")

    print("discovering runs …", flush=True)
    runs, extras = discover_runs(source_root, args.limit, args.max_tiffs_per_run)
    print(f"run dirs      : {len(runs)}", flush=True)

    if args.dry_run:
        run_dry_run(runs, extras)
        return

    # verify auth
    me = client.get_json("/v2/users/me")
    print(f"auth ok       : {me.get('effectiveDisplayName')} ({me.get('appId')})")

    # seed (or resolve) the collection
    if args.no_seed:
        # resolve appId + numeric by name
        col_appid = col_numeric = None
        page = 0
        while True:
            rows = client.get_json(f"/shepard/api/collections?page={page}&size=100")
            if not rows:
                break
            for c in rows:
                if c.get("name") == COLLECTION_NAME:
                    col_appid, col_numeric = c["appId"], c["id"]
            if col_appid or len(rows) < 100:
                break
            page += 1
        if not col_appid:
            sys.stderr.write(f"ERROR: --no-seed but {COLLECTION_NAME} not found\n")
            sys.exit(1)
        print(f"collection    : resolved {COLLECTION_NAME} "
              f"(appId={col_appid}, id={col_numeric})")
    else:
        col_appid, col_numeric = ensure_collection(client, dry=False,
                                                   verbose=args.verbose)

    # collection PublicReadable (IMPORT-PERMS-1). v1 PUT — no v2 collection-perms
    # endpoint; references inherit the collection's permission, so this one call
    # makes every imported file readable to demo (non-admin) users. Read-modify-write
    # to preserve owner/reader/writer lists.
    try:
        cur = client.get_json(f"/shepard/api/collections/{col_numeric}/permissions")
        cur["permissionType"] = "PublicReadable"
        client.request("PUT", f"/shepard/api/collections/{col_numeric}/permissions",
                       json=cur, expect=(200, 201, 204), retry_on_403=True)
        print(f"collection    : set PublicReadable")
    except RuntimeError as e:
        print(f"  [perms WARN] collection: {e}", file=sys.stderr, flush=True)

    live = build_live_index(client, verbose=True)

    state = State(args.state)
    counts = Counts()
    imp = Importer(client, state, counts, col_appid, col_numeric, live, args.verbose,
                   frame_workers=args.frame_workers)

    total = len(runs)
    print(f"\n── ingesting {total} runs ──", flush=True)
    done = 0
    do_appids: dict[str, str] = {}
    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        futs = {ex.submit(imp.process, r): r for r in runs}
        for fut in as_completed(futs):
            r = futs[fut]
            try:
                do_appids[r.source_id] = fut.result()
            except Exception as e:
                counts.add(errors=1)
                state.put_run(r.source_id, "", "error")
                print(f"  [ERROR] {r.source_id}: {e}", file=sys.stderr, flush=True)
            done += 1
            if done % 5 == 0 or done == total:
                now = free_gb(SHEPARD_ROOT)
                print(f"  {done}/{total}  dos={counts.dos_created} "
                      f"files={counts.file_refs} tiffs={counts.tiffs_uploaded} "
                      f"GB={counts.bytes_uploaded/1e9:.1f} err={counts.errors} "
                      f"df={now:.0f}GB", flush=True)
                if now < args.disk_floor_gb:
                    print(f"  [STOP] disk free {now:.1f} GB < floor "
                          f"{args.disk_floor_gb} GB — aborting.", flush=True)
                    break

    # wire predecessors (pass-1 → pass-2 within position/variant/side groups)
    print("\n── wiring predecessors ──", flush=True)
    wire_predecessors(client, col_appid, runs, do_appids, counts, args.verbose)

    print("\n── summary ──")
    print(f"  collection appId    : {col_appid}")
    print(f"  runs ingested       : {counts.runs_done}")
    print(f"  runs reused         : {counts.runs_reused}")
    print(f"  DataObjects created : {counts.dos_created}")
    print(f"  DataObjects reused  : {counts.dos_reused}")
    print(f"  file references     : {counts.file_refs} (reused {counts.file_refs_reused})")
    print(f"  frame bundles       : {counts.bundles_created} (reused {counts.bundles_reused})")
    print(f"  frames in bundles   : {counts.frames_uploaded} (reused {counts.frames_reused})")
    print(f"  tiffs uploaded      : {counts.tiffs_uploaded}")
    print(f"  scope uploaded      : {counts.scope_uploaded}")
    print(f"  videos uploaded     : {counts.videos_uploaded}")
    print(f"  bytes uploaded      : {counts.bytes_uploaded/1e9:.2f} GB")
    print(f"  annotations         : {counts.annotations} (fail {counts.annotation_failures})")
    print(f"  predecessors        : {counts.predecessors}")
    print(f"  short-uploads fixed : {counts.short_uploads}")
    print(f"  permissions set     : {counts.perms_set}")
    print(f"  errors              : {counts.errors}")


if __name__ == "__main__":
    main()
