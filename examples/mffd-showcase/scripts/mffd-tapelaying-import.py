#!/usr/bin/env python3
"""mffd-tapelaying-import.py — import the MFFD "AFP tapelaying" wave (W2) into a
freshly-reset live Shepard instance, from LOCAL data only.

This is the largest MFFD wave (8457 DataObjects: Layup → Ply_N → Track_NN__Run).
It runs in two passes:

  PASS B  structure + lineage (near-zero disk)
          1. create one :DataObject per source directory, carrying its
             ``attributes`` map (v2 create body) + a stable
             ``urn:shepard:source:cube3-do-id`` + ``urn:shepard:source:provenance``
             semantic annotation.
          2. build a full ``cube3-id -> appId`` (and numeric-id) map.
          3. reconstruct the process lineage: for each DO, wire its
             predecessors (appId-native ``predecessorAppIds`` merge-patch) and
             its parent (numeric ``parentId`` merge-patch). Successor and child
             edges form automatically as the reverse of those edges, so they are
             NOT written explicitly (writing them would double-count).

  PASS A  parse TPS raw-data line-scan PNGs -> BrushTrace SpatialDataContainers,
          attached to each Track DataObject. DELEGATES to the existing
          ``plugins/spatial-importer`` decoder + uploader (reuse-before-reimplement).
          GUARDED BY A DISK WATCHDOG: ``df`` is checked before every track; if
          free space on the Shepard root volume drops below ``--disk-floor-gb``
          (default 40 GB) the pass STOPS immediately. The raw 355 GB TPS payloads
          are NEVER uploaded (deferred — backlog row W2-TPS-RAW-1).

Reference implementations reused (see CLAUDE.md "reuse before reimplement"):
  - examples/mffd-showcase/scripts/mffd-bridge-replay.py — the ``Client`` /
    ``load_api_key`` / LiveIndex idempotency helpers + the cube3-do-id
    annotation idempotency contract.
  - plugins/spatial-importer/cli/{linescan,main}.py — the TPS line-scan PNG
    decoder + the SpatialDataContainer/payload uploader (pass A delegates here).

API surface
-----------
Builds on the v2 surface, addressing entities by ``appId``:
  - DataObject create : ``POST /v2/collections/{cid}/data-objects`` ({name,
    description, attributes}) → 201 with ``appId``.
  - Lineage (predecessors + parent) : ``PATCH /v2/collections/{cid}/data-objects/
    {appId}`` (RFC 7396 merge-patch). ``predecessorAppIds`` is the appId-native
    predecessor list (overrides numeric ``predecessorIds``); ``parentId`` is
    numeric-only (the v2 IO has no ``parentAppId`` field — documented gap).
  - Annotations : ``POST /v2/annotations`` ({subjectAppId, subjectKind=DataObject,
    predicateIri, objectLiteral}). subjectKind=Reference is a permanent 403, so
    annotations only ever land on the DataObject.
  - Numeric-id resolution (for ``parentId``) : the frozen v1
    ``GET /shepard/api/collections/{numericId}/dataObjects?page&size`` list,
    which carries BOTH ``id`` and ``appId``. v1-only because the v2 DO IO
    suppresses a DataObject's own numeric id, and ``parentId`` is numeric.

Idempotency
-----------
Idempotent against LIVE Shepard (not a local state file):
  - every DO carries ``urn:shepard:source:cube3-do-id`` so a re-run finds it
    again (LiveIndex, ported from the bridge importer) and reuses instead of
    re-creating.
  - lineage edges are set with REPLACE semantics (the merge-patch
    ``predecessorAppIds`` replaces the predecessor set; ``parentId`` replaces the
    parent), so re-running converges and never duplicates an edge.
  - pass A delegates to the spatial importer's
    ``existing_promotion`` (MERGE on do+sha256), so re-runs upload no dupes.

Usage
-----
    mffd-tapelaying-import.py --dry-run
    mffd-tapelaying-import.py --limit 5 --pass B --verbose
    mffd-tapelaying-import.py --pass B
    mffd-tapelaying-import.py --pass A          # requires the spatial REST surface
    mffd-tapelaying-import.py --pass both
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Optional

try:
    import requests
except ImportError:
    sys.stderr.write("ERROR: this script needs `requests`.  pip install requests\n")
    sys.exit(2)

# ── defaults ────────────────────────────────────────────────────────────────
DEFAULT_HOST = "https://shepard-api.nuclide.systems"
DEFAULT_SOURCE = "/mnt/pve/unas/dump/dataset/cube3-export/mffd-export/ts-export/tapelaying"
DEFAULT_KEYFILE = "/root/.claude/uploads/mffd-import-key-2026-06-17.txt"
TARGET_COLLECTION_APPID = "019ed455-66f4-7aea-8cb3-5c0b34a737df"
DATASET_REL_PREFIX = "cube3-export/mffd-export/ts-export/tapelaying"

PROVENANCE_PREDICATE = "urn:shepard:source:provenance"
SOURCE_DO_ID_PREDICATE = "urn:shepard:source:cube3-do-id"

# Shepard root volume to watch in pass A. The whole instance (Neo4j + Mongo +
# Timescale + POSIX file storage) lives on this volume; filling it crashes the
# instance, so pass A aborts before it gets close.
SHEPARD_ROOT = "/opt/shepard"
DEFAULT_DISK_FLOOR_GB = 40.0

# Spatial-importer CLI (pass A delegate). Resolved relative to this repo.
SPATIAL_IMPORTER_CLI = (
    Path(__file__).resolve().parents[3] / "plugins" / "spatial-importer" / "cli" / "main.py"
)


# ── HTTP client (ported from mffd-bridge-replay.py: retry-forever backoff) ────
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
        error — EXCEPT a transient 403 right after DataObject creation, where the
        :Permissions node wiring can briefly lag (retried with ``retry_on_403``)."""
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
    dos_created: int = 0
    dos_reused: int = 0
    annotations: int = 0
    annotation_failures: int = 0
    predecessor_edges: int = 0
    parent_edges: int = 0
    edges_skipped_out_of_set: int = 0
    lineage_patched: int = 0
    _lock: threading.Lock = field(default_factory=threading.Lock, repr=False)

    def add(self, **kw):
        with self._lock:
            for k, v in kw.items():
                setattr(self, k, getattr(self, k) + v)


# ── LiveIndex (ported from mffd-bridge-replay.py: idempotency against live) ───
class LiveIndex:
    """Snapshot of what already exists in LIVE Shepard for the target collection.
    by_source_id: cube3-do-id literal -> DO appId. by_name: name -> DO appId."""

    def __init__(self, client: Client, collection_appid: str):
        self.c = client
        self.col = collection_appid
        self.by_source_id: dict[str, str] = {}
        self.by_name: dict[str, str] = {}

    def build(self, verbose: bool = False):
        page = 0
        n = 0
        while True:
            batch = self.c.get_json(
                f"/v2/collections/{self.col}/data-objects?page={page}&pageSize=500"
            )
            if not batch:
                break
            for d in batch:
                self.by_name[d["name"]] = d["appId"]
            n += len(batch)
            page += 1
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
        hit = self.by_source_id.get(str(do_id))
        if hit:
            return hit
        return self.by_name.get(do_name)


# ── source loading ──────────────────────────────────────────────────────────
@dataclass
class SourceDO:
    folder: str            # directory name (stable key; matches hierarchy/manifest)
    do_id: int             # cube3 numeric id (metadata.id)
    name: str              # metadata.name (human-readable)
    description: str
    attributes: dict[str, str]
    predecessor_ids: list[int]
    successor_ids: list[int]
    parent_id: Optional[int]
    children_ids: list[int]
    rel_path: str          # dataset-relative provenance path


def load_source(source_root: Path, limit: Optional[int]) -> list[SourceDO]:
    out: list[SourceDO] = []
    dirs = sorted(d for d in source_root.iterdir() if d.is_dir())
    for d in dirs:
        meta_path = d / "metadata.json"
        if not meta_path.is_file():
            continue
        with meta_path.open(encoding="utf-8") as fh:
            m = json.load(fh)
        attrs = m.get("attributes") or {}
        # coerce all attribute values to strings (v2 attributes is string->string)
        attrs = {str(k): ("" if v is None else str(v)) for k, v in attrs.items()}
        out.append(SourceDO(
            folder=d.name,
            do_id=int(m["id"]),
            name=m.get("name") or d.name,
            description=m.get("description") or "",
            attributes=attrs,
            predecessor_ids=[int(x) for x in (m.get("predecessorIds") or [])],
            successor_ids=[int(x) for x in (m.get("successorIds") or [])],
            parent_id=(int(m["parentId"]) if m.get("parentId") is not None else None),
            children_ids=[int(x) for x in (m.get("childrenIds") or [])],
            rel_path=f"{DATASET_REL_PREFIX}/{d.name}",
        ))
        if limit is not None and len(out) >= limit:
            break
    return out


# ── PASS B : structure + lineage ─────────────────────────────────────────────
class StructureImporter:
    def __init__(self, client: Client, counts: Counts, col_appid: str,
                 col_numeric: int, live: Optional[LiveIndex], verbose: bool):
        self.c = client
        self.n = counts
        self.col = col_appid
        self.col_numeric = col_numeric
        self.live = live
        self.verbose = verbose
        # cube3 do_id -> appId, built across the whole create pass (thread-safe).
        self._map_lock = threading.Lock()
        self.id_to_appid: dict[int, str] = {}

    def _annotate(self, subject_appid: str, predicate: str, value: str) -> bool:
        body = {
            "subjectAppId": subject_appid,
            "subjectKind": "DataObject",
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
                print(f"  [annotation WARN] {subject_appid}: {e}", file=sys.stderr, flush=True)
            return False

    # -- sub-pass 1: create one DO, idempotent against live -------------------
    def create_do(self, s: SourceDO):
        live_appid = self.live.find_do(s.do_id, s.name) if self.live else None
        if live_appid:
            with self._map_lock:
                self.id_to_appid[s.do_id] = live_appid
            self.n.add(dos_reused=1)
            # If the DO exists but is missing its source-id annotation (e.g. a
            # half-finished prior run), (re)write it so a later run finds it.
            already = self.live and str(s.do_id) in self.live.by_source_id
            if not already:
                self._annotate(live_appid, SOURCE_DO_ID_PREDICATE, str(s.do_id))
                self._annotate(live_appid, PROVENANCE_PREDICATE, s.rel_path)
                if self.live:
                    self.live.by_source_id[str(s.do_id)] = live_appid
            return

        body: dict[str, Any] = {"name": s.name, "attributes": s.attributes}
        if s.description:
            body["description"] = s.description
        created = self.c.post_json(
            f"/v2/collections/{self.col}/data-objects", body, retry_on_403=True
        )
        appid = created["appId"]
        with self._map_lock:
            self.id_to_appid[s.do_id] = appid
        self.n.add(dos_created=1)
        # annotations: source-id last (its presence proves provenance was written too)
        self._annotate(appid, PROVENANCE_PREDICATE, s.rel_path)
        self._annotate(appid, SOURCE_DO_ID_PREDICATE, str(s.do_id))
        if self.live:
            self.live.by_name[s.name] = appid
            self.live.by_source_id[str(s.do_id)] = appid

    # -- numeric-id resolution for parent wiring (v1, appId-joined) -----------
    def build_numeric_map(self) -> dict[int, int]:
        """appId -> numeric id, harvested from the frozen v1 collection-DO list.
        Needed only for the numeric ``parentId`` merge-patch field (the v2 IO has
        no parentAppId). Single paginated pass; v1-only by necessity."""
        appid_to_numeric: dict[str, int] = {}
        page = 0
        while True:
            batch = self.c.get_json(
                f"/shepard/api/collections/{self.col_numeric}/dataObjects?page={page}&size=500"
            )
            if not batch:
                break
            for d in batch:
                if d.get("appId") and d.get("id") is not None:
                    appid_to_numeric[d["appId"]] = d["id"]
            page += 1
        # project onto cube3 do_id -> numeric id
        out: dict[int, int] = {}
        for do_id, appid in self.id_to_appid.items():
            num = appid_to_numeric.get(appid)
            if num is not None:
                out[do_id] = num
        return out

    # -- sub-pass 2: wire lineage (predecessors appId-native + parent numeric) -
    def wire_lineage(self, s: SourceDO, numeric_map: dict[int, int]):
        appid = self.id_to_appid.get(s.do_id)
        if appid is None:
            return  # DO was not created (shouldn't happen post-create)

        patch: dict[str, Any] = {}

        # predecessors — appId-native; skip any whose target isn't in this set
        pred_appids: list[str] = []
        for pid in s.predecessor_ids:
            target = self.id_to_appid.get(pid)
            if target is None:
                self.n.add(edges_skipped_out_of_set=1)
                if self.verbose:
                    print(f"  [edge skip] {s.folder} predecessor cube3-id {pid} "
                          f"not in collection set", file=sys.stderr, flush=True)
                continue
            pred_appids.append(target)
        if pred_appids:
            patch["predecessorAppIds"] = pred_appids

        # parent — numeric-only field (no parentAppId in the v2 IO)
        if s.parent_id is not None:
            parent_num = numeric_map.get(s.parent_id)
            if parent_num is None:
                self.n.add(edges_skipped_out_of_set=1)
                if self.verbose:
                    print(f"  [edge skip] {s.folder} parent cube3-id {s.parent_id} "
                          f"not in collection set / unresolved numeric",
                          file=sys.stderr, flush=True)
            else:
                patch["parentId"] = parent_num

        if not patch:
            return

        self.c.patch_json(
            f"/v2/collections/{self.col}/data-objects/{appid}", patch, retry_on_403=True
        )
        self.n.add(lineage_patched=1)
        if "predecessorAppIds" in patch:
            self.n.add(predecessor_edges=len(patch["predecessorAppIds"]))
        if "parentId" in patch:
            self.n.add(parent_edges=1)


def run_pass_b(client, counts, col_appid, col_numeric, sources, workers,
               live, verbose) -> StructureImporter:
    imp = StructureImporter(client, counts, col_appid, col_numeric, live, verbose)

    # sub-pass 1: create all DOs
    total = len(sources)
    print(f"\n── pass B / sub-pass 1: create {total} DataObjects ──", flush=True)
    done = 0
    errors: list[tuple[str, str]] = []
    with ThreadPoolExecutor(max_workers=workers) as ex:
        futs = {ex.submit(imp.create_do, s): s for s in sources}
        for fut in as_completed(futs):
            s = futs[fut]
            try:
                fut.result()
            except Exception as e:
                errors.append((s.folder, str(e)))
                print(f"  [HARD ERROR] create {s.folder}: {e}", file=sys.stderr, flush=True)
            done += 1
            if done % 250 == 0 or done == total:
                print(f"  created {done}/{total} "
                      f"(new={counts.dos_created} reused={counts.dos_reused})", flush=True)
    if errors:
        raise RuntimeError(f"{len(errors)} DataObject creates failed; aborting before lineage")

    # build numeric map (for parent wiring)
    print("── pass B / building cube3-id -> numeric-id map (v1 list) ──", flush=True)
    numeric_map = imp.build_numeric_map()
    print(f"  resolved {len(numeric_map)}/{len(imp.id_to_appid)} numeric ids", flush=True)

    # sub-pass 2: wire lineage — SERIALIZED (max_workers=1).
    # Predecessor/parent edges are bidirectional: setting a node's predecessor
    # auto-writes the inverse has_successor on the target. Running this in
    # parallel races those inverse writes and the backend's merge-patch rejects
    # with 400 "the given list of successors does not match the current list"
    # mid-write. Serial wiring is correct and still fast (one PATCH per DO).
    print(f"── pass B / sub-pass 2: wire lineage for {total} DataObjects (serial) ──", flush=True)
    done = 0
    with ThreadPoolExecutor(max_workers=1) as ex:
        futs = {ex.submit(imp.wire_lineage, s, numeric_map): s for s in sources}
        for fut in as_completed(futs):
            s = futs[fut]
            try:
                fut.result()
            except Exception as e:
                errors.append((s.folder, str(e)))
                print(f"  [HARD ERROR] lineage {s.folder}: {e}", file=sys.stderr, flush=True)
            done += 1
            if done % 250 == 0 or done == total:
                print(f"  wired {done}/{total} "
                      f"(pred_edges={counts.predecessor_edges} "
                      f"parent_edges={counts.parent_edges} "
                      f"skipped={counts.edges_skipped_out_of_set})", flush=True)
    if errors:
        raise RuntimeError(f"{len(errors)} lineage patches failed")
    return imp


# ── PASS A : TPS line-scan -> BrushTrace (delegated + disk-guarded) ──────────
def free_gb(path: str) -> float:
    total, used, free = shutil.disk_usage(path)
    return free / 1e9


def spatial_surface_available(client: Client) -> bool:
    """The spatial REST surface is provided by the spatiotemporal plugin. Probe
    its list endpoint; a 404 means the plugin is not enabled in this deployment
    and pass A cannot ingest."""
    try:
        client.request("GET", "/shepard/api/spatialDataContainers", expect=(200,))
        return True
    except RuntimeError as e:
        if "-> 404" in str(e):
            return False
        # any other status: surface — could be auth, treat as unavailable but loud
        print(f"  [pass A probe] unexpected: {e}", file=sys.stderr, flush=True)
        return False


def run_pass_a(args, source_root: Path, col_appid: str):
    print("\n── pass A: TPS line-scan -> BrushTrace ──", flush=True)
    before = free_gb(SHEPARD_ROOT)
    print(f"  disk free (start): {before:.1f} GB  (floor={args.disk_floor_gb} GB)", flush=True)

    client = Client(args.host, load_api_key(args.api_key, args.keyfile), verbose=args.verbose)
    if not spatial_surface_available(client):
        print("  [STOP] the spatial REST surface (/shepard/api/spatialDataContainers) "
              "returns 404 — the spatiotemporal plugin is NOT enabled in this "
              "deployment. Pass A cannot ingest. Pass B structure+lineage stands "
              "as the deliverable; pass A is a documented follow-up "
              "(enable the spatiotemporal plugin, then re-run --pass A).", flush=True)
        return False

    if before < args.disk_floor_gb:
        print(f"  [STOP] disk free {before:.1f} GB already below floor "
              f"{args.disk_floor_gb} GB — refusing to start pass A.", flush=True)
        return False

    # Delegate to the spatial-importer CLI per Track folder, checking disk before
    # each track. The CLI is idempotent (MERGE on do+sha256) and uploads compact
    # per-row BrushTrace points, never the raw PNG bytes.
    if not SPATIAL_IMPORTER_CLI.is_file():
        print(f"  [STOP] spatial-importer CLI not found at {SPATIAL_IMPORTER_CLI}", flush=True)
        return False

    env = dict(os.environ)
    env["SHEPARD_API_KEY"] = load_api_key(args.api_key, args.keyfile)
    env["SHEPARD_URL"] = args.host

    # Drive the delegated CLI in df-guarded BATCHES of --limit tracks. The CLI's
    # --limit takes the first N Track folders in sorted order and is idempotent
    # (MERGE on do+sha256), so re-invoking with a growing limit advances through
    # the corpus while skipping already-promoted chunks. Between every batch we
    # re-check df and abort the moment free space dips below the floor — the
    # CLI streams compact per-row BrushTrace points and never the raw PNG bytes,
    # but the guard is the hard stop the brief mandates.
    all_tracks = sorted(
        d for d in source_root.iterdir() if d.is_dir() and d.name.startswith("Track_")
    )
    total_tracks = len(all_tracks) if args.limit is None else min(args.limit, len(all_tracks))
    print(f"  {total_tracks} Track folders to process (batch size {args.batch_tracks})", flush=True)

    processed = 0
    stopped = False
    while processed < total_tracks:
        now = free_gb(SHEPARD_ROOT)
        if now < args.disk_floor_gb:
            print(f"  [STOP] disk free {now:.1f} GB < floor {args.disk_floor_gb} GB "
                  f"after {processed}/{total_tracks} tracks. Halting pass A.", flush=True)
            stopped = True
            break
        batch_limit = min(processed + args.batch_tracks, total_tracks)
        cmd = [
            sys.executable, str(SPATIAL_IMPORTER_CLI),
            "--linescan-pass",
            "--collection-app-id", col_appid,
            "--source", str(source_root),
            "--workers", str(args.workers),
            "--limit", str(batch_limit),   # idempotent: re-skips promoted chunks
        ]
        if args.verbose:
            print(f"  [batch] df={now:.1f}GB  -> spatial-importer --limit {batch_limit}", flush=True)
        rc = subprocess.call(cmd, env=env)
        if rc != 0:
            print(f"  [STOP] spatial-importer exited {rc} on batch ending {batch_limit}; "
                  f"halting pass A.", flush=True)
            stopped = True
            break
        processed = batch_limit

    after = free_gb(SHEPARD_ROOT)
    print(f"  disk free (end): {after:.1f} GB  (delta {after-before:+.1f} GB)", flush=True)
    print(f"  tracks processed: {processed}/{total_tracks}", flush=True)
    return not stopped


# ── dry-run accounting ────────────────────────────────────────────────────────
def run_dry_run(sources: list[SourceDO], source_root: Path):
    n_dos = len(sources)
    by_id = {s.do_id for s in sources}
    pred_edges = 0
    parent_edges = 0
    skipped = 0
    for s in sources:
        for pid in s.predecessor_ids:
            if pid in by_id:
                pred_edges += 1
            else:
                skipped += 1
        if s.parent_id is not None:
            if s.parent_id in by_id:
                parent_edges += 1
            else:
                skipped += 1
    # pass-A accounting: TPS raw data files + bytes
    tps_files = 0
    tps_bytes = 0
    for s in sources:
        files_dir = source_root / s.folder / "files"
        if not files_dir.is_dir():
            continue
        for f in files_dir.iterdir():
            if f.is_file() and f.name.startswith("TPS raw data."):
                tps_files += 1
                try:
                    tps_bytes += f.stat().st_size
                except OSError:
                    pass
    print("\n── DRY-RUN plan ──")
    print(f"  DataObjects to create/reuse : {n_dos}")
    print(f"  predecessor edges (in-set)  : {pred_edges}")
    print(f"  parent edges (in-set)       : {parent_edges}")
    print(f"  edges skipped (out-of-set)  : {skipped}")
    print(f"  TPS raw-data files (pass A) : {tps_files}")
    print(f"  TPS raw-data bytes (pass A) : {tps_bytes} ({tps_bytes/1e9:.2f} GB)  "
          f"[NEVER uploaded — parsed to BrushTrace; raw deferred W2-TPS-RAW-1]")


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
    ap = argparse.ArgumentParser(description="Import the MFFD AFP-tapelaying wave (W2) into Shepard.")
    ap.add_argument("--source", default=DEFAULT_SOURCE)
    ap.add_argument("--host", default=DEFAULT_HOST)
    ap.add_argument("--api-key", default=None)
    ap.add_argument("--keyfile", default=DEFAULT_KEYFILE)
    ap.add_argument("--collection-appid", default=TARGET_COLLECTION_APPID)
    ap.add_argument("--pass", dest="which_pass", choices=["B", "A", "both"], default="both")
    ap.add_argument("--workers", type=int, default=6)
    ap.add_argument("--limit", type=int, default=None, help="only first N source dirs / tracks")
    ap.add_argument("--disk-floor-gb", type=float, default=DEFAULT_DISK_FLOOR_GB,
                    help="pass A aborts when Shepard-root free space drops below this")
    ap.add_argument("--batch-tracks", type=int, default=200,
                    help="pass A: df is re-checked between every batch of this many tracks")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--verbose", action="store_true")
    args = ap.parse_args()

    source_root = Path(args.source)
    api_key = load_api_key(args.api_key, args.keyfile)
    client = Client(args.host, api_key, verbose=args.verbose)

    print(f"source        : {source_root}")
    print(f"host          : {args.host}")
    print(f"collection    : {args.collection_appid}")
    print(f"pass          : {args.which_pass}")
    print(f"mode          : {'DRY-RUN' if args.dry_run else 'LIVE'}  workers={args.workers}"
          f"{'  limit='+str(args.limit) if args.limit else ''}")

    # verify auth + collection
    me = client.get_json("/v2/users/me")
    print(f"auth ok       : {me.get('effectiveDisplayName')} ({me.get('appId')})")
    col = client.get_json(f"/v2/collections/{args.collection_appid}")
    print(f"collection ok : {col.get('name')}")

    # resolve numeric collection id (for v1 parent-numeric resolution)
    col_list = client.get_json(
        f"/shepard/api/collections?name={requests.utils.quote(col['name'])}"
    )
    col_numeric = next(
        (c["id"] for c in col_list if c.get("appId") == args.collection_appid), None
    )
    if col_numeric is None:
        sys.stderr.write("ERROR: could not resolve collection numeric id\n")
        sys.exit(1)
    print(f"collection #id: {col_numeric}")

    print("loading source …", flush=True)
    sources = load_source(source_root, args.limit)
    print(f"source DOs    : {len(sources)}", flush=True)

    if args.dry_run:
        run_dry_run(sources, source_root)
        print(f"  disk free now : {free_gb(SHEPARD_ROOT):.1f} GB", flush=True)
        return

    counts = Counts()

    if args.which_pass in ("B", "both"):
        live = LiveIndex(client, args.collection_appid)
        live.build(verbose=True)
        print(f"live index    : {len(live.by_name)} existing DOs, "
              f"{len(live.by_source_id)} source-id annotations", flush=True)
        run_pass_b(client, counts, args.collection_appid, col_numeric,
                   sources, args.workers, live, args.verbose)
        print("\n── pass B summary ──")
        print(f"  DataObjects created     : {counts.dos_created}")
        print(f"  DataObjects reused      : {counts.dos_reused}")
        print(f"  annotations written     : {counts.annotations}")
        print(f"  annotation failures     : {counts.annotation_failures}")
        print(f"  lineage patches         : {counts.lineage_patched}")
        print(f"  predecessor edges       : {counts.predecessor_edges}")
        print(f"  parent edges            : {counts.parent_edges}")
        print(f"  edges skipped out-of-set: {counts.edges_skipped_out_of_set}")

    if args.which_pass in ("A", "both"):
        ok = run_pass_a(args, source_root, args.collection_appid)
        if not ok:
            print("\npass A did not complete (see above). Pass B remains a valid deliverable.")


if __name__ == "__main__":
    main()
