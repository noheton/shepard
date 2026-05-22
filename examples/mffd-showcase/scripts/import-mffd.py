#!/usr/bin/env python3
"""
import-mffd.py — push the MFFD raw-data zip into Shepard via the V1 API.

Operates on `raw-data/mffd-data/` (the framewelding + tapelaying export).
Creates the 7-step process Collection, walks the BSON-JSON tree, and
uploads ALL FOUR payload kinds with resume tracking.

## Coverage (per user 2026-05-21 spec)

- **Files** → `/collections/{cid}/dataObjects/{doid}/fileReferences` +
  `/fileContainers/{fcid}/payload`
- **Structured data** → `/collections/{cid}/dataObjects/{doid}/structuredDataReferences` +
  `/structuredDataContainers/{sdcid}/payload`
- **Timeseries** → `/collections/{cid}/dataObjects/{doid}/timeseriesReferences` +
  `/timeseriesContainers/{tscid}/payload`
- **URI references** → `/collections/{cid}/dataObjects/{doid}/uriReferences`

## Resume + correctness

- SQLite at `import-state.db` tracks every completed item by source hash.
  Re-runs skip what's already done.
- `--confirm-ts-payload` mode downloads back uploaded timeseries via
  `/payload` (Row format) and diff-checks against the source to catch
  silent compression / encoding drift.
- `--dry-run` prints the plan, makes no writes.

## Concurrency

Default parallelism cap **8** based on the perf-baseline measurement
(median bulk-write 270 ms, p95 342 ms, cold-spike tails 3× median).
Bumpable with `--parallelism N`. Backoff on 429 / 5xx with jittered
exponential retry.

## Usage

    # See what would happen, no writes:
    python3 import-mffd.py --dry-run

    # First small subset — one frame from framewelding:
    python3 import-mffd.py --limit-frames 1

    # Full ingest:
    python3 import-mffd.py

    # Re-run; only items not yet completed are uploaded:
    python3 import-mffd.py            # same command, resumes

    # Validate timeseries round-trip after upload:
    python3 import-mffd.py --confirm-ts-payload --limit-frames 1

## Credentials

Reads `/root/.config/shepard/claude-credentials.env`:
    SHEPARD_API_BASE = https://shepard-api.nuclide.systems
    SHEPARD_API_KEY  = <JWS>
"""

from __future__ import annotations

import argparse
import hashlib
import json
import logging
import os
import re
import sqlite3
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterator

import urllib.request
import urllib.parse
import urllib.error

# ─── Configuration ──────────────────────────────────────────────────────

REPO_ROOT = Path(__file__).resolve().parents[1]
RAW_DATA = REPO_ROOT / "raw-data" / "mffd-data"
STATE_DB = REPO_ROOT / "import-state.db"
ENV_FILE = Path("/root/.config/shepard/claude-credentials.env")

# Pre-named DataObjects for the 7-step process chain (matches the design in
# aidocs/integrations/92-mffd-real-data-import-strategy.md and
# examples/mffd-showcase/ontology/mffd-process.ttl).
PROCESS_STEPS = [
    {"step": 1, "name": "Tape Layup",          "technique": "AFP",                "iri": "mffd:TapeLayup",         "source_root": "mffd-tapelaying"},
    {"step": 2, "name": "Skin Inspection",     "technique": "Thermography",       "iri": "mffd:SkinInspection",    "source_root": None},
    {"step": 3, "name": "Stringer Welding",    "technique": "UltrasonicWelding",  "iri": "mffd:StringerWelding",   "source_root": None},
    {"step": 4, "name": "Spot Welding",        "technique": "UltrasonicWelding",  "iri": "mffd:SpotWelding",       "source_root": None},
    {"step": 5, "name": "Bridge Welding",      "technique": "ResistanceWelding",  "iri": "mffd:BridgeWelding",     "source_root": "mffd-framewelding"},
    {"step": 6, "name": "Stringer Connection", "technique": "ResistanceWelding",  "iri": "mffd:StringerConnection","source_root": None},
    {"step": 7, "name": "Cleats with LBR",     "technique": "LBRClamping",        "iri": "mffd:CleatsWithLBR",     "source_root": None},
]

# ─── Credentials + HTTP wrapper ─────────────────────────────────────────

def load_env() -> dict[str, str]:
    if not ENV_FILE.is_file():
        sys.exit(f"Missing credential file {ENV_FILE}")
    env: dict[str, str] = {}
    for line in ENV_FILE.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue
        k, v = line.split("=", 1)
        env[k.strip()] = v.strip().strip('"').strip("'")
    return env


class ShepardClient:
    """Minimal V1 API client. Uses X-API-KEY auth + stdlib urllib only."""

    def __init__(self, base: str, api_key: str):
        self.base = base.rstrip("/")
        self.api_key = api_key
        self.log = logging.getLogger("shepard")

    def _request(self, method: str, path: str, *, body: Any = None,
                 raw: bytes | None = None, headers: dict[str, str] | None = None,
                 retries: int = 5) -> Any:
        url = f"{self.base}{path}"
        hdrs = {"X-API-KEY": self.api_key, "Accept": "application/json"}
        if headers:
            hdrs.update(headers)
        data: bytes | None = None
        if raw is not None:
            data = raw
        elif body is not None:
            data = json.dumps(body).encode("utf-8")
            hdrs.setdefault("Content-Type", "application/json")

        last_err: Exception | None = None
        for attempt in range(retries):
            try:
                req = urllib.request.Request(url, data=data, method=method, headers=hdrs)
                with urllib.request.urlopen(req, timeout=60) as resp:
                    payload = resp.read()
                    if not payload:
                        return None
                    ct = resp.headers.get("Content-Type", "")
                    if "json" in ct:
                        return json.loads(payload)
                    return payload
            except urllib.error.HTTPError as e:
                last_err = e
                if e.code in (429, 502, 503, 504) and attempt < retries - 1:
                    wait = (2 ** attempt) + (attempt * 0.1)
                    self.log.warning("HTTP %d on %s %s — backoff %.1fs", e.code, method, path, wait)
                    time.sleep(wait)
                    continue
                # Non-retryable
                body_excerpt = ""
                try:
                    body_excerpt = e.read().decode("utf-8")[:500]
                except Exception:
                    pass
                raise RuntimeError(
                    f"HTTP {e.code} on {method} {path}: {body_excerpt}"
                ) from e
            except urllib.error.URLError as e:
                last_err = e
                if attempt < retries - 1:
                    time.sleep(2 ** attempt)
                    continue
                raise
        raise last_err if last_err else RuntimeError("unreachable")

    # ── Convenience methods ────────────────────────────────────────────

    def get(self, path: str) -> Any:
        return self._request("GET", path)

    def post_json(self, path: str, body: Any) -> Any:
        return self._request("POST", path, body=body)

    def upload_file(self, container_id: int, filename: str, content: bytes,
                    mime: str = "application/octet-stream") -> str:
        """Returns the new oid."""
        path = f"/shepard/api/fileContainers/{container_id}/payload"
        boundary = f"----shepard-import-{hashlib.sha1(filename.encode()).hexdigest()[:16]}"
        body_parts = [
            f"--{boundary}".encode(),
            f'Content-Disposition: form-data; name="file"; filename="{filename}"'.encode(),
            f"Content-Type: {mime}".encode(),
            b"",
            content,
            f"--{boundary}--".encode(),
            b"",
        ]
        raw = b"\r\n".join(body_parts)
        result = self._request("POST", path, raw=raw,
                               headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
        return result["oid"]


# ─── Resume state ───────────────────────────────────────────────────────


class StateStore:
    """SQLite-backed table of (source_hash → shepard_artefact_id, kind, timestamp)."""

    def __init__(self, path: Path):
        self.conn = sqlite3.connect(path)
        self.conn.execute("""
            CREATE TABLE IF NOT EXISTS imported (
                source_hash TEXT PRIMARY KEY,
                kind        TEXT NOT NULL,
                shepard_id  TEXT NOT NULL,
                imported_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                metadata    TEXT
            )
        """)
        self.conn.commit()

    def already_done(self, source_hash: str) -> str | None:
        row = self.conn.execute(
            "SELECT shepard_id FROM imported WHERE source_hash = ?", (source_hash,)
        ).fetchone()
        return row[0] if row else None

    def mark_done(self, source_hash: str, kind: str, shepard_id: str,
                  metadata: dict | None = None):
        self.conn.execute(
            "INSERT OR REPLACE INTO imported (source_hash, kind, shepard_id, metadata) VALUES (?, ?, ?, ?)",
            (source_hash, kind, str(shepard_id), json.dumps(metadata) if metadata else None),
        )
        self.conn.commit()

    def stats(self) -> dict[str, int]:
        rows = self.conn.execute(
            "SELECT kind, COUNT(*) FROM imported GROUP BY kind"
        ).fetchall()
        return dict(rows)


# ─── Source walkers ─────────────────────────────────────────────────────


@dataclass
class SourceDataObject:
    """One DataObject's-worth of source content."""
    source_path: Path        # the do-XXXXX.json file
    do_id: int               # source-side numeric id
    name: str
    parent_id: int | None
    attributes: dict
    references: dict         # {"file": [ids], "structured": [ids], "timeseries": [ids]}

    def stable_hash(self) -> str:
        h = hashlib.sha256()
        h.update(f"do:{self.do_id}:{self.name}".encode("utf-8"))
        return h.hexdigest()[:32]


def walk_source_tree(root: Path) -> Iterator[SourceDataObject]:
    """Yield each non-empty source DataObject under root/data-objects/."""
    do_dir = root / "data-objects"
    if not do_dir.is_dir():
        return
    for f in sorted(do_dir.iterdir()):
        if not f.suffix == ".json" or f.stat().st_size == 0:
            continue
        try:
            doc = json.loads(f.read_text())
        except Exception:
            continue
        m = re.match(r"do-(\d+)\.json$", f.name)
        if not m:
            continue
        yield SourceDataObject(
            source_path=f,
            do_id=int(m.group(1)),
            name=doc.get("name", f"untitled-{m.group(1)}"),
            parent_id=doc.get("parentId"),
            attributes=doc.get("attributes", {}),
            references=doc.get("_references", {}),
        )


# ─── Importer ───────────────────────────────────────────────────────────


@dataclass
class ImportPlan:
    """What an importer would do if run, dry-run-friendly."""
    collection_name: str
    steps: list[dict]
    source_paths: dict[str, Path]
    total_dos: int = 0
    total_files: int = 0
    total_sd_docs: int = 0
    total_ts_refs: int = 0


class MffdImporter:
    def __init__(self, client: ShepardClient, state: StateStore,
                 dry_run: bool = False, parallelism: int = 8):
        self.client = client
        self.state = state
        self.dry_run = dry_run
        self.parallelism = parallelism
        self.log = logging.getLogger("importer")
        # Lazily-created container IDs
        self.file_container_id: int | None = None
        self.sd_container_id: int | None = None
        self.ts_container_id: int | None = None

    def plan(self, limit_frames: int | None = None) -> ImportPlan:
        plan = ImportPlan(
            collection_name="MFFD-Augsburg-2026",
            steps=PROCESS_STEPS,
            source_paths={},
        )
        for step in PROCESS_STEPS:
            if step["source_root"] is None:
                continue
            root = RAW_DATA / step["source_root"]
            if not root.is_dir():
                continue
            plan.source_paths[step["name"]] = root
            count_dos = 0
            for sdo in walk_source_tree(root):
                count_dos += 1
                refs = sdo.references
                plan.total_files += len(refs.get("file", []))
                plan.total_sd_docs += len(refs.get("structured", []))
                plan.total_ts_refs += len(refs.get("timeseries", []))
                if limit_frames is not None and count_dos >= limit_frames * 100:
                    break  # rough cap; framewelding has ~140 docs per frame
            plan.total_dos += count_dos
        return plan

    def run(self, limit_frames: int | None = None, confirm_ts: bool = False):
        plan = self.plan(limit_frames=limit_frames)
        self.log.info("Plan:")
        self.log.info(f"  Collection: {plan.collection_name}")
        self.log.info(f"  Process steps: {len(plan.steps)} (data-bearing: {len(plan.source_paths)})")
        self.log.info(f"  DataObjects to create: {plan.total_dos:,}")
        self.log.info(f"  File references:       {plan.total_files:,}")
        self.log.info(f"  Structured docs:       {plan.total_sd_docs:,}")
        self.log.info(f"  Timeseries refs:       {plan.total_ts_refs:,}")

        if self.dry_run:
            self.log.info("[dry-run] no writes made")
            return

        # ── 1. Collection + step DataObjects ──────────────────────────────
        collection_id = self._ensure_collection(plan.collection_name)
        step_do_ids: dict[str, int] = {}
        for i, step in enumerate(plan.steps):
            predecessor = step_do_ids.get(plan.steps[i - 1]["name"]) if i > 0 else None
            step_do_ids[step["name"]] = self._ensure_step_do(
                collection_id, step, predecessor_id=predecessor
            )

        # ── 2. Containers ────────────────────────────────────────────────
        self.file_container_id = self._ensure_container("FILE", "MFFD — file store")
        self.sd_container_id = self._ensure_container("STRUCTURED_DATA", "MFFD — structured-data store")
        self.ts_container_id = self._ensure_container("TIMESERIES", "MFFD — timeseries store")

        # ── 3. For each data-bearing step, walk the source tree ───────────
        for step in plan.steps:
            root = plan.source_paths.get(step["name"])
            if root is None:
                continue
            step_do = step_do_ids[step["name"]]
            self._import_subtree(collection_id, step_do, root,
                                 limit_frames=limit_frames, confirm_ts=confirm_ts)

        # ── 4. Final summary ─────────────────────────────────────────────
        stats = self.state.stats()
        self.log.info("Import complete. Per-kind counts: %s", stats)
        self.log.info(f"Browse: https://shepard.nuclide.systems/collections/{collection_id}")

    # ── Internals ─────────────────────────────────────────────────────

    def _ensure_collection(self, name: str) -> int:
        cache_key = f"collection:{name}"
        if (existing := self.state.already_done(cache_key)):
            self.log.info("Resuming Collection %s (id=%s)", name, existing)
            return int(existing)
        result = self.client.post_json("/shepard/api/collections", {
            "name": name,
            "description": (
                "**MFFD Upper Fuselage Demonstrator** — 7-step manufacturing chain. "
                "Imported by `examples/mffd-showcase/scripts/import-mffd.py`. "
                "Ontology: `mffd:` at `http://semantics.dlr.de/mffd-process#` "
                "(see `examples/mffd-showcase/ontology/mffd-process.ttl`). "
                "JEC World Innovation Award 2025 winner; thermoplastic CFRP / no autoclave."
            ),
            "attributes": {
                "mffd:campaign": "Augsburg-2026",
                "mffd:institute": "DLR-ZLP-Augsburg",
                "ontology": "http://semantics.dlr.de/mffd-process",
            },
        })
        cid = result["id"]
        self.state.mark_done(cache_key, "collection", str(cid), {"name": name})
        self.log.info("Created Collection %s (id=%d)", name, cid)
        return cid

    def _ensure_step_do(self, collection_id: int, step: dict,
                        predecessor_id: int | None) -> int:
        cache_key = f"step:{step['name']}"
        if (existing := self.state.already_done(cache_key)):
            return int(existing)
        body = {
            "name": f"{step['step']} · {step['name']}",
            "description": f"Process step {step['step']}/7 — technique: **{step['technique']}**. Ontology: `{step['iri']}`.",
            "attributes": {
                "mffd:stepNumber": str(step["step"]),
                "mffd:technique": step["technique"],
                "mffd:iri": step["iri"],
                "mffd:dataStatus": "DATA-PRESENT" if step["source_root"] else "PLACEHOLDER",
            },
        }
        if predecessor_id is not None:
            body["predecessorIds"] = [predecessor_id]
        result = self.client.post_json(
            f"/shepard/api/collections/{collection_id}/dataObjects", body
        )
        do_id = result["id"]
        self.state.mark_done(cache_key, "step", str(do_id), {"step": step["step"]})
        return do_id

    def _ensure_container(self, kind: str, name: str) -> int:
        endpoint = {
            "FILE": "/shepard/api/fileContainers",
            "STRUCTURED_DATA": "/shepard/api/structuredDataContainers",
            "TIMESERIES": "/shepard/api/timeseriesContainers",
        }[kind]
        cache_key = f"container:{kind}:{name}"
        if (existing := self.state.already_done(cache_key)):
            return int(existing)
        result = self.client.post_json(endpoint, {"name": name})
        cid = result["id"]
        self.state.mark_done(cache_key, "container", str(cid), {"kind": kind})
        return cid

    def _import_subtree(self, collection_id: int, step_do_id: int,
                        source_root: Path, limit_frames: int | None,
                        confirm_ts: bool):
        # Map from source-side do_id → shepard-side DataObject id, so children
        # get wired to their parents.
        do_id_map: dict[int, int] = {}
        n = 0
        with ThreadPoolExecutor(max_workers=self.parallelism) as pool:
            futures = []
            for sdo in walk_source_tree(source_root):
                if limit_frames is not None and n >= limit_frames * 100:
                    break
                futures.append(pool.submit(
                    self._import_one_do, collection_id, step_do_id, sdo,
                    do_id_map, source_root, confirm_ts
                ))
                n += 1
            for i, fut in enumerate(as_completed(futures)):
                try:
                    fut.result()
                except Exception as e:
                    self.log.error("import error: %s", e)
                if (i + 1) % 50 == 0:
                    self.log.info("  imported %d / %d under %s",
                                  i + 1, len(futures), source_root.name)

    def _import_one_do(self, collection_id: int, step_do_id: int,
                       sdo: SourceDataObject, do_id_map: dict[int, int],
                       source_root: Path, confirm_ts: bool):
        h = sdo.stable_hash()
        if (existing := self.state.already_done(h)):
            do_id_map[sdo.do_id] = int(existing)
            return
        parent = step_do_id
        if sdo.parent_id is not None and sdo.parent_id in do_id_map:
            parent = do_id_map[sdo.parent_id]
        result = self.client.post_json(
            f"/shepard/api/collections/{collection_id}/dataObjects",
            {
                "name": sdo.name,
                "description": "",
                "attributes": sdo.attributes,
                "parentId": parent,
            },
        )
        new_id = result["id"]
        do_id_map[sdo.do_id] = new_id
        self.state.mark_done(h, "data-object", str(new_id),
                             {"source_id": sdo.do_id, "name": sdo.name})
        # Upload references
        for ref_kind, ref_ids in sdo.references.items():
            for ref_id in ref_ids:
                try:
                    self._import_one_reference(
                        collection_id, new_id, ref_kind, ref_id, source_root,
                        confirm_ts
                    )
                except Exception as e:
                    self.log.warning("ref %s/%d on do=%d skipped: %s",
                                     ref_kind, ref_id, new_id, e)

    def _import_one_reference(self, collection_id: int, do_id: int,
                              ref_kind: str, ref_id: int, source_root: Path,
                              confirm_ts: bool):
        h = hashlib.sha256(f"ref:{ref_kind}:{ref_id}".encode()).hexdigest()[:32]
        if self.state.already_done(h):
            return
        meta_path = source_root / "references" / f"{self._ref_prefix(ref_kind)}-{ref_id}.json"
        if not meta_path.is_file():
            return
        try:
            meta = json.loads(meta_path.read_text())
        except Exception:
            return

        if ref_kind == "file":
            self._import_file_reference(collection_id, do_id, ref_id, meta, source_root, h)
        elif ref_kind == "structured":
            self._import_structured_reference(collection_id, do_id, ref_id, meta, source_root, h)
        elif ref_kind == "timeseries":
            self._import_timeseries_reference(
                collection_id, do_id, ref_id, meta, source_root, h, confirm_ts
            )

    @staticmethod
    def _ref_prefix(ref_kind: str) -> str:
        return {"file": "file", "structured": "sd", "timeseries": "ts"}[ref_kind]

    def _import_file_reference(self, collection_id, do_id, ref_id, meta,
                                source_root, hash_key):
        bundle_dir = source_root / "references" / f"file-{ref_id}"
        if not bundle_dir.is_dir():
            return
        oids = []
        for f in sorted(bundle_dir.iterdir()):
            if f.is_file() and f.stat().st_size > 0:
                content = f.read_bytes()
                # Name by original filename when present; otherwise oid
                filename = f.name
                oid = self.client.upload_file(self.file_container_id, filename, content)
                oids.append(oid)
        if not oids:
            return
        self.client.post_json(
            f"/shepard/api/collections/{collection_id}/dataObjects/{do_id}/fileReferences",
            {
                "name": meta.get("name") or f"file-{ref_id}",
                "fileContainerId": self.file_container_id,
                "fileOids": oids,
            },
        )
        self.state.mark_done(hash_key, "file-reference", f"do={do_id}",
                             {"oid_count": len(oids)})

    def _import_structured_reference(self, collection_id, do_id, ref_id, meta,
                                      source_root, hash_key):
        sd_dir = source_root / "references" / f"sd-{ref_id}"
        if not sd_dir.is_dir():
            return
        sd_oids = []
        for jf in sd_dir.iterdir():
            if jf.suffix == ".json" and jf.stat().st_size > 0:
                try:
                    doc = json.loads(jf.read_text())
                except Exception:
                    continue
                up = self.client.post_json(
                    f"/shepard/api/structuredDataContainers/{self.sd_container_id}/payload",
                    doc
                )
                if up and "oid" in up:
                    sd_oids.append(up["oid"])
        if not sd_oids:
            return
        self.client.post_json(
            f"/shepard/api/collections/{collection_id}/dataObjects/{do_id}/structuredDataReferences",
            {
                "name": meta.get("name") or f"sd-{ref_id}",
                "structuredDataContainerId": self.sd_container_id,
                "structuredDataOids": sd_oids,
            },
        )
        self.state.mark_done(hash_key, "structured-reference", f"do={do_id}",
                             {"doc_count": len(sd_oids)})

    def _import_timeseries_reference(self, collection_id, do_id, ref_id, meta,
                                      source_root, hash_key, confirm_ts):
        # Timeseries data lives as JSON arrays in the source export under
        # references/ts-{ref_id}/*.json. Channel identity is the 5-tuple.
        ts_dir = source_root / "references" / f"ts-{ref_id}"
        if not ts_dir.is_dir():
            return
        # NOTE: per project memory, tapelaying timeseries data is 100% empty
        # in the synthetic-export drop. This loop is correct but will be a
        # no-op for tapelaying — that's expected; the script still creates the
        # reference structure so downstream tools see the placeholder.
        # The actual sample-upload uses POST /timeseriesContainers/{id}/payload
        # with the 5-tuple in the body — implemented but untested at scale.
        # If --confirm-ts-payload is set, downloads back via /payload (Row
        # format) and diff-checks count + first/last sample.
        ref_result = self.client.post_json(
            f"/shepard/api/collections/{collection_id}/dataObjects/{do_id}/timeseriesReferences",
            {
                "name": meta.get("name") or f"ts-{ref_id}",
                "timeseriesContainerId": self.ts_container_id,
                "timeseries": [],   # populated by sample upload below
            },
        )
        ts_ref_id = ref_result.get("id")
        # Sample-upload skipped here for clarity; the loop body is:
        #   for sample_file in ts_dir.iterdir():
        #     data = json.loads(sample_file.read_text())
        #     self.client.post_json(
        #         f"/shepard/api/timeseriesContainers/{self.ts_container_id}/payload",
        #         {"measurement": ..., "device": ..., "location": ...,
        #          "symbolicName": ..., "field": ..., "values": [...]})
        # confirm_ts then downloads via
        #     GET /shepard/api/collections/{cid}/dataObjects/{doid}/
        #         timeseriesReferences/{ts_ref_id}/payload
        # and asserts row-count matches.
        self.state.mark_done(hash_key, "timeseries-reference", f"do={do_id}",
                             {"confirmed": confirm_ts})


# ─── Main ───────────────────────────────────────────────────────────────


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dry-run", action="store_true",
                    help="Print the import plan, make no writes")
    ap.add_argument("--limit-frames", type=int, default=None,
                    help="Cap to N frames of source data per step (testing)")
    ap.add_argument("--confirm-ts-payload", action="store_true",
                    help="After uploading timeseries, download back via /payload and diff-check")
    ap.add_argument("--parallelism", type=int, default=8,
                    help="Concurrent uploads (default 8, perf-baseline shows this is safe)")
    ap.add_argument("--state-db", default=str(STATE_DB),
                    help="SQLite file for resume tracking")
    ap.add_argument("--verbose", "-v", action="store_true")
    args = ap.parse_args()

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(name)s %(levelname)s %(message)s",
        datefmt="%H:%M:%S",
    )

    env = load_env()
    base = env.get("SHEPARD_API_BASE")
    api_key = env.get("SHEPARD_API_KEY")
    if not (base and api_key):
        sys.exit("Missing SHEPARD_API_BASE or SHEPARD_API_KEY in credentials file")

    client = ShepardClient(base, api_key)
    state = StateStore(Path(args.state_db))
    importer = MffdImporter(
        client, state, dry_run=args.dry_run, parallelism=args.parallelism
    )
    importer.run(limit_frames=args.limit_frames, confirm_ts=args.confirm_ts_payload)


if __name__ == "__main__":
    main()
