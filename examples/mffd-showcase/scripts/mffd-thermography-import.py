#!/usr/bin/env python3
r"""
mffd-thermography-import.py — import the MFFD "NDT thermography" (W6) wave into a
freshly-reset live Shepard instance, from LOCAL data only.

This is the wave after spot-welding (W8a, ``mffd-spotwelding-import.py``) and
bridge-welding (W3, ``mffd-bridge-replay.py``). It reuses the proven idempotency
discipline from those scripts: the ``Client`` (retry-forever backoff) and
``load_api_key`` helpers are imported directly from the bridge script (which is
NOT modified), and the ``State`` / ``LiveIndex`` / reuse-before-create patterns
mirror the spot-welding importer — the closest analogue, since both waves rely on
"upload an artefact, the server-side FileParserPlugin enriches it" rather than a
manifest replay.

Source (LOCAL, read-only)
-------------------------
``/mnt/pve/unas/dump/dataset/thermography-extracted/`` — the extracted
thermography.7z. The READMEs example root was one level off (it referenced a
``thermography/`` prefix); the REAL classified tree lives at the top of
``thermography-extracted/``:

  - ``process/<layer>/…``     for layers L8 L9 L11 L15 L18 L19 L19-plus (707 files)
  - ``references/<scope>/…``  for scopes parameter truth referenzbauteil
                              antenna doorcorners (37 files)

744 OTvis files total. (A second, FLAT mirror ``thermography/MFFD/`` holds the
same 744 files as hardlinks — same inodes — so it is IGNORED; the classified
tree is the canonical source because it carries scope + layer in the path.)

A ``manifest.json`` sits in ``thermography-extracted/`` and drives the
classification: ``totals`` (196 planned / 198 captured cells, 707 frames),
``gap_audit`` (under/over/unplanned captures → capture-incomplete /
capture-substitution status flags, keyed by (section, module, layer) cell),
and ``typo_normalisations`` (3 files whose on-disk filename is the ORIGINAL/typo
form, mapping to the corrected name). All status/typo annotations are driven from
this manifest, never hardcoded.

How thermography ingestion works in the CURRENT codebase
--------------------------------------------------------
Same shape as svdx (W8a): there is NO ``shepard-importer --source thermography``
CLI (the README's example is fictional) and NO dedicated thermography ingest REST
endpoint. Uploading a ``.OTvis`` as a singleton FileReference (multipart
``POST /v2/files?parentDataObjectAppId=…&name=…``) automatically triggers the
``fileformat-thermography`` ``OTvisParser`` server-side:
``SingletonFileReferenceService`` calls ``FileParserRegistry`` → ``OTvisParser``,
which reads the embedded ``content.xml`` (UTF-16 acquisition manifest) and the
``S<n>_M<n>_L<n>_F<n>.OTvis`` filename grid, then writes:
  - ``urn:shepard:thermography:*`` acquisition annotations (frame rate, integration
    time, excitation device/frequency/amplitude/signal, recording type, resolution,
    conditioning/acquisition periods, campaign, moduleName, creatingVersion,
    createdAt) — on the FileReference appId.
  - ``urn:shepard:mffd:{section,module,layer,frame}`` grid annotations — on the
    parent DataObject appId — ONLY when the filename matches the grid regex
    ``(?i)S\d+_M\d+_L\d+\+?_F\d+\.OTvis``. The 37 reference-scope files (named
    e.g. ``Parameter (10).OTvis``) and 2 of the 3 typo files (missing underscore:
    ``S13_M10L9_F2``, ``S5_M5L19+_F4``) do NOT match, so they get only the
    ``thermography:*`` acquisition set from the parser. Our own scope/layer
    annotations (below) cover classification for those independently.

KNOWN GAP: ``POST /v2/annotations`` with ``subjectKind=Reference`` returns a
permanent 403 even for instance-admin. So EVERY annotation this importer writes
goes on the DATAOBJECT, never on the FileReference. (The parser's own writes go
wherever its callback targets — that's server-side and unaffected.)

What this importer creates
--------------------------
One DataObject per OTvis file (name = on-disk filename, typos preserved), with the
OTvis uploaded as a singleton FileReference (FR1b, auto-parsed). On each
DataObject it writes (all on the DataObject):
  - ``urn:shepard:source:provenance   = <dataset-relative-path>``
  - ``urn:shepard:source:otvis-file   = <basename>``  (stable cross-run key)
  - process scope:  ``urn:shepard:mffd:scope = process`` + ``urn:shepard:mffd:layer = <n>``
    (layer number, ``L`` stripped, ``L19-plus`` → ``19+``)
  - reference scope: ``urn:shepard:mffd:scope = <scope>``  (parameter|truth|…)
  - ``urn:shepard:mffd:measurement-phase = pre|post`` for process files, derived
    from the content.xml CreationDate (earlier calendar day = pre, later = post).
    Reference-scope files have no plan grid → phase skipped + logged.
  - status flags from the manifest, on exactly the listed files:
    ``urn:shepard:status:capture-incomplete=true`` (under-capture cells),
    ``urn:shepard:status:capture-substitution=true`` (over/unplanned cells),
    ``urn:shepard:filename:typo-corrected=true`` + ``urn:shepard:filename:original=<orig>``
    (the 3 typo files).

Idempotency
-----------
The ``otvis-file`` basename is the stable source id. A ``LiveIndex`` built at
startup maps that literal → DO appId (via the source-id annotation), DO name →
appId, and each live DO's existing file-reference labels. Reuse-before-create: a
DO present in the index is reused; a file ref already attached (confirmed by a
fresh re-read to defeat the by-data-object projection's lag) is skipped;
annotation writes are guarded by the source-id index. Re-runs converge to exactly
744 DOs + 744 file refs and create ZERO new graph nodes.

Usage
-----
    python3 mffd-thermography-import.py --dry-run
    python3 mffd-thermography-import.py --limit 3 --verbose
    python3 mffd-thermography-import.py            # full run, all 744 files
"""
from __future__ import annotations

import argparse
import os
import sqlite3
import subprocess
import sys
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from typing import Optional

try:
    import requests
except ImportError:
    sys.stderr.write("ERROR: this script needs `requests`.  pip install requests\n")
    sys.exit(2)

# Reuse the proven Client (retry-forever backoff) + key loader from the bridge
# replay script, which lives alongside this one. We do NOT modify it.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from importlib import import_module

_bridge = import_module("mffd-bridge-replay")
Client = _bridge.Client  # type: ignore[attr-defined]
load_api_key = _bridge.load_api_key  # type: ignore[attr-defined]

import json  # noqa: E402  (after the sys.path insert for clarity)

# ── defaults ──────────────────────────────────────────────────────────────────
DEFAULT_HOST = "https://shepard-api.nuclide.systems"
DEFAULT_SOURCE_DIR = "/mnt/pve/unas/dump/dataset/thermography-extracted"
DEFAULT_KEYFILE = "/root/.claude/uploads/mffd-import-key-2026-06-17.txt"
TARGET_COLLECTION_APPID = "019ed455-6866-71f1-b0bf-0f83a3e3aaa9"
# provenance paths are dataset-relative from the dataset root
DATASET_ROOT_MARKER = "thermography-extracted"

PROVENANCE_PREDICATE = "urn:shepard:source:provenance"
# Stable cross-run source id — the thermography analogue of the bridge wave's
# urn:shepard:source:cube3-do-id and the spot-welding wave's source:svdx-file.
SOURCE_OTVIS_FILE_PREDICATE = "urn:shepard:source:otvis-file"

# mffd / status predicates (we own these; parser owns urn:shepard:thermography:*
# and urn:shepard:mffd:{section,module,layer,frame})
PRED_SCOPE = "urn:shepard:mffd:scope"
PRED_LAYER = "urn:shepard:mffd:layer"
PRED_PHASE = "urn:shepard:mffd:measurement-phase"
PRED_CAPTURE_INCOMPLETE = "urn:shepard:status:capture-incomplete"
PRED_CAPTURE_SUBSTITUTION = "urn:shepard:status:capture-substitution"
PRED_TYPO_CORRECTED = "urn:shepard:filename:typo-corrected"
PRED_TYPO_ORIGINAL = "urn:shepard:filename:original"

DEFAULT_STATE = os.path.expanduser("~/.mffd-thermography-import-state.db")

REFERENCE_SCOPES = {"parameter", "truth", "referenzbauteil", "antenna", "doorcorners"}


# ── thread-safe sqlite resumable state ──────────────────────────────────────────
class State:
    """Resumable progress, keyed by the OTvis basename.

      dos(basename TEXT PK, do_appid TEXT, annotated INTEGER)
      file_refs(basename TEXT PK, ref_appid TEXT, uploaded INTEGER)
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
              basename TEXT PRIMARY KEY, ref_appid TEXT, uploaded INTEGER DEFAULT 0);
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
                "SELECT ref_appid, uploaded FROM file_refs WHERE basename=?", (basename,)
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


# ── manifest-driven classifier ──────────────────────────────────────────────────
def _norm_layer(layer: str) -> str:
    """Normalise a layer token to its annotation form: strip a leading 'L',
    map the directory form 'L19-plus' / '19-plus' to '19+'."""
    s = str(layer)
    if s.lower().startswith("l"):
        s = s[1:]
    s = s.replace("-plus", "+").replace("plus", "+")
    return s


class Manifest:
    """Loads thermography-extracted/manifest.json and answers per-file
    classification questions. All status/typo logic is driven from here so the
    importer carries no hardcoded cell lists."""

    def __init__(self, manifest_path: str, verbose: bool = False):
        self.verbose = verbose
        self.loaded = False
        # cell-key (section:int, module:int, layer:str-normalised) -> flag
        self.incomplete_cells: set = set()
        self.substitution_cells: set = set()
        # original-basename -> corrected-basename
        self.typo_orig_to_corrected: dict[str, str] = {}
        self.totals: dict = {}
        if os.path.exists(manifest_path):
            self._load(manifest_path)
        elif verbose:
            print(f"  [manifest WARN] not found at {manifest_path} — "
                  f"falling back to path-derived classification only",
                  file=sys.stderr, flush=True)

    def _load(self, path: str):
        d = json.load(open(path, encoding="utf-8"))
        self.loaded = True
        self.totals = d.get("totals", {})
        gap = d.get("gap_audit", {})
        for u in gap.get("under_captures", []):
            self.incomplete_cells.add(
                (int(u["section"]), int(u["module"]), _norm_layer(u["layer"])))
        # over_captures + unplanned_captures both → capture-substitution per README §332
        for u in gap.get("over_captures", []) + gap.get("unplanned_captures", []):
            self.substitution_cells.add(
                (int(u["section"]), int(u["module"]), _norm_layer(u["layer"])))
        # typo_normalisations maps ORIGINAL (on-disk) filename -> corrected name
        for orig, corrected in d.get("typo_normalisations", {}).items():
            self.typo_orig_to_corrected[orig] = corrected
        if self.verbose:
            print(f"  manifest      : {len(self.incomplete_cells)} incomplete + "
                  f"{len(self.substitution_cells)} substitution cells, "
                  f"{len(self.typo_orig_to_corrected)} typo files",
                  file=sys.stderr, flush=True)

    def cell_status(self, section: int, module: int, layer_norm: str) -> Optional[str]:
        key = (section, module, layer_norm)
        if key in self.incomplete_cells:
            return "incomplete"
        if key in self.substitution_cells:
            return "substitution"
        return None

    def typo_corrected_name(self, basename: str) -> Optional[str]:
        return self.typo_orig_to_corrected.get(basename)


import re  # noqa: E402

_GRID_RE = re.compile(r"(?i)^S(\d+)_M(\d+)_L(\d+\+?)_F(\d+)\.OTvis$")
# tolerant variant for the typo files (missing underscore before/after L) so we
# can still locate the cell for status/phase classification even when the strict
# parser regex (which our classification does NOT depend on) would reject them.
_GRID_RE_LOOSE = re.compile(r"(?i)^S(\d+)_M(\d+)_?L(\d+\+?)_?F(\d+)\.OTvis$")


def parse_grid(basename: str):
    """Return (section:int, module:int, layer_norm:str, frame:int) or None.
    Uses the loose regex so the 3 typo filenames still classify."""
    m = _GRID_RE.match(basename) or _GRID_RE_LOOSE.match(basename)
    if not m:
        return None
    return (int(m.group(1)), int(m.group(2)), _norm_layer("L" + m.group(3)), int(m.group(4)))


def read_creation_day(abspath: str) -> Optional[str]:
    """Read content.xml's CreationDate from the OTvis tar and return the date as
    a sortable 'YYYY-MM-DD' key for pre/post clustering. The Edevis value is
    rendered MM/DD/YYYY (US, per README: 02/04 = Feb 4, 02/06 = Feb 6).

    NOTE: the OTvis tar carries a leading ``HIDDEN TOC`` SpecialFile that Python's
    stdlib ``tarfile`` mis-sizes — it stops after the first member and never sees
    ``content.xml``. GNU ``tar`` (and the Java parser's Apache Commons Compress)
    handle it fine, so we shell out to ``tar xfO <file> content.xml`` rather than
    use ``tarfile``. Returns None on any read failure (phase then logged, not
    guessed)."""
    try:
        proc = subprocess.run(
            ["tar", "xfO", abspath, "content.xml"],
            capture_output=True, timeout=120, check=False)
        raw = proc.stdout
        if not raw:
            return None
    except (subprocess.SubprocessError, OSError):
        return None
    # content.xml is UTF-16 LE; decode loosely and grab CreationDate.
    try:
        if len(raw) >= 2 and raw[0] == 0xFF and raw[1] == 0xFE:
            text = raw[2:].decode("utf-16-le", errors="replace")
        else:
            text = raw.decode("utf-16-le", errors="replace")
    except Exception:
        return None
    m = re.search(r"reationDate>\s*([0-9]{2})/([0-9]{2})/([0-9]{4})", text)
    if not m:
        return None
    mm, dd, yyyy = m.group(1), m.group(2), m.group(3)
    # sortable key: YYYY-MM-DD (raw field order MM/DD/YYYY)
    return f"{yyyy}-{mm}-{dd}"


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
    phase_pre: int = 0
    phase_post: int = 0
    phase_undeterminable: int = 0
    status_incomplete: int = 0
    status_substitution: int = 0
    typo_flagged: int = 0
    _lock: threading.Lock = field(default_factory=threading.Lock, repr=False)

    def add(self, **kw):
        with self._lock:
            for k, v in kw.items():
                setattr(self, k, getattr(self, k) + v)


# ── live-Shepard index (idempotency against LIVE state) ─────────────────────────
class LiveIndex:
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
        live_do_appids: set[str] = set()
        while True:
            batch = self.c.get_json(
                f"/v2/collections/{self.col}/data-objects?page={page}&pageSize=500"
            )
            if not batch:
                break
            for d in batch:
                self.by_name[d["name"]] = d["appId"]
                do_appids.append(d["appId"])
                live_do_appids.add(d["appId"])
            n += len(batch)
            page += 1

        def _fetch(appid):
            try:
                lst = self.c.get_json(f"/v2/files/by-data-object/{appid}")
                return appid, {x.get("name") for x in lst
                               if isinstance(lst, list) and x.get("name")}
            except RuntimeError:
                return appid, set()

        if do_appids:
            with ThreadPoolExecutor(max_workers=self.workers) as ex:
                for appid, names in ex.map(_fetch, do_appids):
                    self.file_refs_by_do[appid] = names

        # Source-id annotations: the /v2/annotations endpoint returns annotations
        # whose subject DataObject may be soft-deleted (a tombstone). Reusing such
        # a subject 404s on the subsequent file upload (observed 2026-06-17 — 4
        # files tried to reuse 4 deleted test DOs). So keep ONLY annotations whose
        # subject is in the LIVE DataObject set; a stale tombstone falls through to
        # a fresh create.
        page = 0
        anns = 0
        stale = 0
        while True:
            batch = self.c.get_json(
                f"/v2/annotations?predicateIri={requests.utils.quote(SOURCE_OTVIS_FILE_PREDICATE)}"
                f"&page={page}&pageSize=200"
            )
            if not batch:
                break
            for a in batch:
                lit = a.get("objectLiteral")
                subj = a.get("subjectAppId")
                if lit and subj:
                    if subj in live_do_appids:
                        self.by_source_id[lit] = subj
                    else:
                        stale += 1
            anns += len(batch)
            page += 1
        if verbose:
            print(f"live index    : {n} existing DOs, {anns} source-id annotations "
                  f"({len(self.by_source_id)} live, {stale} stale/tombstone dropped)",
                  file=sys.stderr, flush=True)

    def find_do(self, basename: str) -> Optional[str]:
        hit = self.by_source_id.get(basename)
        if hit:
            return hit
        return self.by_name.get(basename)


# ── per-file classification record ──────────────────────────────────────────────
@dataclass
class FileClass:
    abspath: str
    basename: str
    relpath: str           # dataset-relative provenance path
    scope: str             # "process" or one of REFERENCE_SCOPES
    layer: Optional[str]   # normalised layer for process scope, else None
    grid: Optional[tuple]  # (section, module, layer_norm, frame) or None
    status: Optional[str]  # "incomplete" | "substitution" | None
    typo_original: Optional[str]   # on-disk original name if typo, else None
    typo_corrected: Optional[str]  # corrected name if typo, else None


# ── importer ────────────────────────────────────────────────────────────────────
class Importer:
    def __init__(self, client: Client, state: State, counts: Counts,
                 collection_appid: str, dry_run: bool, verbose: bool,
                 derive_phase: bool, live: Optional[LiveIndex] = None):
        self.c = client
        self.st = state
        self.n = counts
        self.col = collection_appid
        self.dry = dry_run
        self.verbose = verbose
        self.derive_phase = derive_phase
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

    def _annotate(self, do_appid: str, predicate: str, value: str,
                  count_field: str = "annotations") -> bool:
        """Write one annotation ON THE DATAOBJECT (Reference annotations 403)."""
        if self.dry:
            return True
        body = {
            "subjectAppId": do_appid,
            "subjectKind": "DataObject",
            "predicateIri": predicate,
            "objectLiteral": value,
            "sourceMode": "ai",
        }
        try:
            self.c.post_json("/v2/annotations", body)
            self.n.add(**{count_field: 1})
            return True
        except RuntimeError as e:
            self.n.add(annotation_failures=1)
            if self.verbose:
                print(f"  [annotation WARN] DO {do_appid} {predicate}: {e}",
                      file=sys.stderr, flush=True)
            return False

    def import_file(self, fc: FileClass):
        basename = fc.basename
        size = os.path.getsize(fc.abspath)

        if self.dry:
            self.n.add(dos_created=1, file_refs=1, bytes_uploaded=size)
            if fc.status == "incomplete":
                self.n.add(status_incomplete=1)
            elif fc.status == "substitution":
                self.n.add(status_substitution=1)
            if fc.typo_original:
                self.n.add(typo_flagged=1)
            return

        # 1. DataObject — idempotent: sqlite, then live index, then create.
        cached = self.st.get_do(basename)
        annotated = 0
        do_appid = None
        if cached and cached[0]:
            do_appid, annotated = cached
            self.n.add(dos_reused=1)
        else:
            live_appid = self.live.find_do(basename) if self.live else None
            if live_appid:
                do_appid = live_appid
                self.st.put_do(basename, do_appid)
                self.n.add(dos_reused=1)
            else:
                created = self.c.post_json(
                    f"/v2/collections/{self.col}/data-objects", {"name": basename})
                do_appid = created["appId"]
                self.st.put_do(basename, do_appid)
                self.n.add(dos_created=1)
                if self.live:
                    self.live.by_name[basename] = do_appid

        # 2. annotations on the DataObject. Guarded by the live source-id index
        # (source:otvis-file written last) so a re-run writes ZERO annotations.
        already_annotated = self.live and basename in self.live.by_source_id
        if not annotated and not already_annotated:
            self._do_annotations(do_appid, fc)
            if self.live:
                self.live.by_source_id[basename] = do_appid
            self.st.mark_do_annotated(basename)

        # 3. singleton FileReference (FR1b). The server-side fileformat-thermography
        # parser fires automatically and emits urn:shepard:thermography:* (+ mffd
        # grid where the filename matches).
        fcached = self.st.get_file(basename)
        ref_appid = uploaded = None
        if fcached:
            ref_appid, uploaded = fcached

        ref_label = basename
        if not fcached:
            if ref_label in self._existing_file_ref_names(do_appid):
                self.n.add(file_refs=1, file_refs_reused=1)
                return
            if ref_label in self._existing_file_ref_names(do_appid, refresh=True):
                self.n.add(file_refs=1, file_refs_reused=1)
                return

        if not ref_appid or not uploaded:
            with open(fc.abspath, "rb") as fh:
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

    def _do_annotations(self, do_appid: str, fc: FileClass):
        # provenance + stable source id
        self._annotate(do_appid, PROVENANCE_PREDICATE, fc.relpath)
        # scope + layer
        if fc.scope == "process":
            self._annotate(do_appid, PRED_SCOPE, "process")
            if fc.layer:
                self._annotate(do_appid, PRED_LAYER, fc.layer)
            # measurement-phase from CreationDate clustering (process only)
            if self.derive_phase:
                phase = self._phase_for(fc)
                if phase == "pre":
                    self._annotate(do_appid, PRED_PHASE, "pre")
                    self.n.add(phase_pre=1)
                elif phase == "post":
                    self._annotate(do_appid, PRED_PHASE, "post")
                    self.n.add(phase_post=1)
                else:
                    self.n.add(phase_undeterminable=1)
                    if self.verbose:
                        print(f"  [phase] undeterminable for {fc.basename} "
                              f"(no readable CreationDate) — skipping phase annotation",
                              file=sys.stderr, flush=True)
        else:
            self._annotate(do_appid, PRED_SCOPE, fc.scope)
            # reference-scope files have no plan grid → no phase (logged, not guessed)
            if self.verbose:
                print(f"  [phase] reference-scope {fc.basename} — phase not "
                      f"derivable, skipped", file=sys.stderr, flush=True)

        # status flags
        if fc.status == "incomplete":
            self._annotate(do_appid, PRED_CAPTURE_INCOMPLETE, "true",
                           count_field="status_incomplete")
        elif fc.status == "substitution":
            self._annotate(do_appid, PRED_CAPTURE_SUBSTITUTION, "true",
                           count_field="status_substitution")
        # typo correction
        if fc.typo_original and fc.typo_corrected:
            self._annotate(do_appid, PRED_TYPO_CORRECTED, "true",
                           count_field="typo_flagged")
            self._annotate(do_appid, PRED_TYPO_ORIGINAL, fc.typo_original)

        # stable source id LAST (its presence proves the rest were issued)
        self._annotate(do_appid, SOURCE_OTVIS_FILE_PREDICATE, fc.basename)

    def _phase_for(self, fc: FileClass) -> Optional[str]:
        """Cluster pre/post on the file's CreationDate calendar day. The campaign
        has two capture days (pre-thermal-cycle and post); the earlier day = pre,
        the later = post. We do not hardcode the dates — we compare against the
        per-layer min/max established lazily? No: simplest robust rule that matches
        the README and the data — Feb-4 day == pre, Feb-6 day == post — generalised
        to: any date whose day-of-month < the corpus split == pre. To avoid a
        global pre-pass we use the documented two-day split directly: read the
        date, and classify by which of the two known campaign days it falls on."""
        day = read_creation_day(fc.abspath)
        if day is None:
            return None
        # The corpus has exactly two capture days: 2023-02-04 (pre) and
        # 2023-02-06 (post) per README §117. Classify by the day component.
        # Robust to either being absent: earlier day -> pre, later -> post.
        # 2023-02-04 / 2023-02-05* -> pre ; 2023-02-06+ -> post.
        if day <= "2023-02-05":
            return "pre"
        return "post"


# ── discovery / classification ──────────────────────────────────────────────────
def classify_files(source_dir: str, manifest: Manifest,
                   verbose: bool = False) -> list[FileClass]:
    process_dir = os.path.join(source_dir, "process")
    refs_dir = os.path.join(source_dir, "references")
    out: list[FileClass] = []

    def _rel(abspath: str) -> str:
        return f"{DATASET_ROOT_MARKER}/{os.path.relpath(abspath, source_dir)}"

    # process scope (one dir per layer)
    if os.path.isdir(process_dir):
        for layer_dir in sorted(os.listdir(process_dir)):
            d = os.path.join(process_dir, layer_dir)
            if not os.path.isdir(d):
                continue
            layer_norm = _norm_layer(layer_dir)  # L19-plus -> 19+
            for fn in sorted(os.listdir(d)):
                if not fn.lower().endswith(".otvis"):
                    continue
                abspath = os.path.join(d, fn)
                grid = parse_grid(fn)
                status = None
                if grid is not None:
                    status = manifest.cell_status(grid[0], grid[1], grid[2])
                typo_corrected = manifest.typo_corrected_name(fn)
                out.append(FileClass(
                    abspath=abspath, basename=fn, relpath=_rel(abspath),
                    scope="process", layer=layer_norm, grid=grid, status=status,
                    typo_original=(fn if typo_corrected else None),
                    typo_corrected=typo_corrected))

    # reference scope (one dir per scope)
    if os.path.isdir(refs_dir):
        for scope_dir in sorted(os.listdir(refs_dir)):
            d = os.path.join(refs_dir, scope_dir)
            if not os.path.isdir(d):
                continue
            scope = scope_dir.lower()
            for fn in sorted(os.listdir(d)):
                if not fn.lower().endswith(".otvis"):
                    continue
                abspath = os.path.join(d, fn)
                typo_corrected = manifest.typo_corrected_name(fn)
                out.append(FileClass(
                    abspath=abspath, basename=fn, relpath=_rel(abspath),
                    scope=scope, layer=None, grid=parse_grid(fn), status=None,
                    typo_original=(fn if typo_corrected else None),
                    typo_corrected=typo_corrected))

    # guard against the flat thermography/MFFD mirror being passed accidentally
    seen_names: dict[str, str] = {}
    for fc in out:
        if fc.basename in seen_names:
            sys.stderr.write(
                f"ERROR: duplicate basename {fc.basename} "
                f"({seen_names[fc.basename]} vs {fc.abspath}) — refusing to import\n")
            sys.exit(1)
        seen_names[fc.basename] = fc.abspath
    return out


# ── orchestration ───────────────────────────────────────────────────────────────
def main():
    ap = argparse.ArgumentParser(
        description="Import the MFFD NDT-thermography (.OTvis, W6) wave into Shepard.")
    ap.add_argument("--source-dir", default=DEFAULT_SOURCE_DIR)
    ap.add_argument("--manifest", default=None,
                    help="manifest.json path (default: <source-dir>/manifest.json)")
    ap.add_argument("--host", default=DEFAULT_HOST)
    ap.add_argument("--api-key", default=None)
    ap.add_argument("--keyfile", default=DEFAULT_KEYFILE)
    ap.add_argument("--collection-appid", default=TARGET_COLLECTION_APPID)
    ap.add_argument("--state", default=DEFAULT_STATE)
    ap.add_argument("--workers", type=int, default=6)
    ap.add_argument("--limit", type=int, default=None, help="only first N files")
    ap.add_argument("--no-phase", action="store_true",
                    help="skip CreationDate-derived measurement-phase annotation")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--verbose", action="store_true")
    args = ap.parse_args()

    api_key = load_api_key(args.api_key, args.keyfile)
    client = Client(args.host, api_key, verbose=args.verbose)
    manifest_path = args.manifest or os.path.join(args.source_dir, "manifest.json")

    print(f"source dir    : {args.source_dir}")
    print(f"manifest      : {manifest_path}")
    print(f"host          : {args.host}")
    print(f"collection    : {args.collection_appid}")
    print(f"state file    : {args.state}")
    print(f"mode          : {'DRY-RUN' if args.dry_run else 'LIVE'}  workers={args.workers}"
          f"{'  limit='+str(args.limit) if args.limit else ''}"
          f"{'  NO-PHASE' if args.no_phase else ''}")

    me = client.get_json("/v2/users/me")
    print(f"auth ok       : {me.get('effectiveDisplayName')} ({me.get('appId')})")
    col = client.get_json(f"/v2/collections/{args.collection_appid}")
    print(f"collection ok : {col.get('name')}")

    manifest = Manifest(manifest_path, verbose=True)
    files = classify_files(args.source_dir, manifest, verbose=args.verbose)
    if args.limit:
        files = files[: args.limit]
    if not files:
        sys.stderr.write("ERROR: no .OTvis files found\n")
        sys.exit(1)

    # breakdown
    by_scope: dict[str, int] = {}
    by_layer: dict[str, int] = {}
    total_bytes = 0
    for fc in files:
        by_scope[fc.scope] = by_scope.get(fc.scope, 0) + 1
        if fc.scope == "process":
            by_layer[fc.layer or "?"] = by_layer.get(fc.layer or "?", 0) + 1
        total_bytes += os.path.getsize(fc.abspath)
    print(f"OTvis files   : {len(files)}  ({total_bytes/1e9:.2f} GB)")
    print(f"  per-scope    : {dict(sorted(by_scope.items()))}")
    print(f"  per-layer    : {dict(sorted(by_layer.items()))}")
    n_proc = sum(1 for f in files if f.scope == "process")
    n_ref = len(files) - n_proc
    print(f"  process={n_proc}  reference={n_ref}")
    print(f"  status flags : incomplete={sum(1 for f in files if f.status=='incomplete')}, "
          f"substitution={sum(1 for f in files if f.status=='substitution')}, "
          f"typo={sum(1 for f in files if f.typo_original)}\n")

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
                   args.dry_run, args.verbose, derive_phase=not args.no_phase,
                   live=live)

    if args.dry_run:
        for fc in files:
            imp.import_file(fc)
        _print_summary(counts, dry=True)
        return

    errors: list[tuple[str, str]] = []
    done = 0
    total = len(files)
    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        futs = {ex.submit(imp.import_file, fc): fc.basename for fc in files}
        for fut in as_completed(futs):
            name = futs[fut]
            try:
                fut.result()
            except Exception as e:
                errors.append((name, str(e)))
                print(f"  [HARD ERROR] {name}: {e}", file=sys.stderr, flush=True)
            done += 1
            if done % 25 == 0 or done == total:
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
    print(f"  DataObjects created       : {c.dos_created}")
    if not dry:
        print(f"  DataObjects reused        : {c.dos_reused}")
    print(f"  file refs                 : {c.file_refs}")
    if not dry:
        print(f"  file refs reused          : {c.file_refs_reused}")
    print(f"  payload bytes             : {c.bytes_uploaded} ({c.bytes_uploaded/1e9:.2f} GB)")
    if not dry:
        print(f"  provenance/scope/layer/phase/status annotations : {c.annotations}")
        print(f"  annotation failures       : {c.annotation_failures}")
        print(f"  phase pre / post / undet. : {c.phase_pre} / {c.phase_post} / {c.phase_undeterminable}")
    print(f"  status capture-incomplete : {c.status_incomplete}")
    print(f"  status capture-substitution: {c.status_substitution}")
    print(f"  typo-corrected files      : {c.typo_flagged}")
    if not dry:
        print("  (urn:shepard:thermography:* + mffd grid annotations are written "
              "server-side by the fileformat-thermography parser)")


if __name__ == "__main__":
    main()
