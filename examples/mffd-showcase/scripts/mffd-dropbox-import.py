#!/usr/bin/env python3
"""mffd-dropbox-import.py — MFFD manufacturing process data ingest with provenance.

Requirements
------------
    pip install requests

Purpose
-------
"Initial commit" ingest for real MFFD tape-laying and bridge-welding data into a
dedicated Shepard dropbox collection.  Establishes the process chain:

    TapeLaying-{session} → BridgeWelding-{session}
                          ↑
                    [PREDECESSOR_OF]

After all files are uploaded, creates a Collection snapshot so future comparisons
against a clean baseline are possible.

Usage
-----
    SHEPARD_URL=https://shepard.nuclide.systems \\
    SHEPARD_API_KEY=<your-key> \\
    SESSION_ID=2026-05-22-Q1 \\
    DATA_DIR=/path/to/mffd-data \\
    python mffd-dropbox-import.py

    # Dry-run (no API calls):
    python mffd-dropbox-import.py --dry-run

Environment variables
---------------------
    SHEPARD_URL       Base URL.  Default: https://shepard.nuclide.systems
    SHEPARD_API_KEY   X-API-KEY.  Required for live run.
    SESSION_ID        Short identifier for this production run.
                      Default: today's date (YYYY-MM-DD).
    DATA_DIR          Directory with subdirs: tapelaying/, bridgewelding/
                      Any unrecognised subdirs go to an "Other-{name}" DataObject
                      with no predecessor link.
    COLLECTION_NAME   Override the collection name.
                      Default: "MFFD-Dropbox"

Process chain
-------------
    The script creates DataObjects in this order and links them:

      TapeLaying-{SESSION_ID}     (AFP robot, consolidation force, TCP temp logs)
           ↓  PREDECESSOR_OF
      BridgeWelding-{SESSION_ID}  (UW current, UW force, joint quality logs)

    Additional subdirs found in DATA_DIR are appended without predecessor links
    so they don't break the canonical chain.

Idempotency
-----------
    - Collection looked up by exact name; created if absent.
    - DataObjects looked up by exact name; created if absent.
    - Predecessor links are set at DataObject creation time via v1 API.
    - Files skip-checked by display name.
    - Snapshot is always created at the end (not idempotent by design —
      each run produces a new snapshot marking the state after that run).
"""

from __future__ import annotations

import argparse
import datetime
import json
import os
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

try:
    import requests
    from requests import Session, Response
except ImportError:
    print("ERROR: pip install requests", file=sys.stderr)
    sys.exit(1)

# ── Configuration ─────────────────────────────────────────────────────────────

SHEPARD_URL = os.environ.get("SHEPARD_URL", "https://shepard.nuclide.systems").rstrip("/")
SHEPARD_API_KEY = os.environ.get("SHEPARD_API_KEY", "")
SESSION_ID = os.environ.get("SESSION_ID", datetime.date.today().isoformat())
DATA_DIR = Path(os.environ.get("DATA_DIR", "."))
COLLECTION_NAME = os.environ.get("COLLECTION_NAME", "MFFD-Dropbox")

# Canonical MFFD process chain — subdirs that match these names are wired into
# the predecessor chain in the order listed here.
PROCESS_CHAIN = [
    "tapelaying",       # AFP robot run — consolidation force, TCP temp, trajectory logs
    "bridgewelding",    # UW bridge welding — current, force, joint quality
]


# ── Data model ────────────────────────────────────────────────────────────────

@dataclass
class Step:
    """One MFFD process step DataObject."""
    key: str                  # e.g. "tapelaying"
    do_name: str              # e.g. "TapeLaying-2026-05-22-Q1"
    predecessor_key: str | None  # key of the step this one follows
    local_dir: Path | None    # DATA_DIR/<key> if it exists
    attributes: dict[str, str] = field(default_factory=dict)

    @property
    def title(self) -> str:
        return self.do_name.replace("-", " ")


# ── HTTP client ───────────────────────────────────────────────────────────────

class ShepardClient:
    """Minimal Shepard client. API key never echoed in output."""

    def __init__(self, base: str, api_key: str) -> None:
        self._base = base
        self._s = Session()
        self._s.headers.update({"X-API-KEY": api_key, "Accept": "application/json"})

    # Collections (v1 API — stable, upstream-compat)

    def find_collection(self, name: str) -> dict | None:
        r = self._get(f"{self._base}/shepard/api/collections", {"name": name})
        if r is None:
            return None
        for c in r.json():
            if c.get("name") == name:
                return c
        return None

    def create_collection(self, name: str, description: str, attrs: dict) -> dict | None:
        body: dict[str, Any] = {"name": name, "description": description}
        if attrs:
            body["attributes"] = attrs
        r = self._post(f"{self._base}/shepard/api/collections", body)
        return r.json() if r else None

    def get_collection_app_id(self, collection_id: int) -> str | None:
        r = self._get(f"{self._base}/shepard/api/collections/{collection_id}")
        if r is None:
            return None
        return r.json().get("appId")

    # DataObjects (v1 API)

    def find_data_object(self, coll_id: int, name: str) -> dict | None:
        r = self._get(
            f"{self._base}/shepard/api/collections/{coll_id}/dataObjects",
            {"name": name},
        )
        if r is None:
            return None
        for d in r.json():
            if d.get("name") == name:
                return d
        return None

    def create_data_object(
        self,
        coll_id: int,
        name: str,
        description: str = "",
        attrs: dict | None = None,
        predecessor_id: int | None = None,
    ) -> dict | None:
        body: dict[str, Any] = {"name": name}
        if description:
            body["description"] = description
        if attrs:
            body["attributes"] = attrs
        r = self._post(
            f"{self._base}/shepard/api/collections/{coll_id}/dataObjects", body
        )
        if r is None:
            return None
        do = r.json()
        # Wire predecessor link immediately after creation
        if predecessor_id is not None:
            self._link_predecessor(coll_id, do["id"], predecessor_id)
        return do

    def _link_predecessor(self, coll_id: int, do_id: int, pred_id: int) -> None:
        url = (
            f"{self._base}/shepard/api/collections/{coll_id}"
            f"/dataObjects/{do_id}/predecessors/{pred_id}"
        )
        r = self._put(url, {})
        if r is not None:
            print(f"  [prov] linked predecessor {pred_id} → {do_id}")
        else:
            print(f"  [warn] predecessor link failed: {pred_id} → {do_id}")

    # Files (v2 singleton — FR1b)

    def list_file_refs(self, coll_id: int, do_id: int) -> set[str]:
        r = self._get(
            f"{self._base}/shepard/api/collections/{coll_id}"
            f"/dataObjects/{do_id}/fileReferences"
        )
        if r is None:
            return set()
        return {ref.get("name", "") for ref in r.json()}

    def upload_file(self, app_id: str, path: Path, display: str) -> bool:
        url = f"{self._base}/v2/files"
        params = {"parentDataObjectAppId": app_id, "name": display}
        try:
            with path.open("rb") as fh:
                r = self._s.post(
                    url, params=params, files={"file": (path.name, fh)}, timeout=600
                )
        except Exception as exc:
            print(f"  [error] upload {path.name}: {exc}")
            return False
        if not r.ok:
            print(f"  [http {r.status_code}] upload {display}: {r.text[:300]}")
            return False
        return True

    # Snapshots (v2)

    def create_snapshot(self, coll_app_id: str, label: str) -> dict | None:
        body = {"label": label, "description": f"Auto-snapshot after MFFD dropbox import — session {SESSION_ID}"}
        r = self._post(f"{self._base}/v2/collections/{coll_app_id}/snapshots", body)
        return r.json() if r else None

    # ── Low-level ─────────────────────────────────────────────────────────────

    def _get(self, url: str, params: dict | None = None) -> Response | None:
        try:
            r = self._s.get(url, params=params, timeout=30)
            if not r.ok:
                self._log_err("GET", url, r)
                return None
            return r
        except Exception as exc:
            print(f"  [net] GET {url}: {exc}")
            return None

    def _post(self, url: str, body: dict) -> Response | None:
        try:
            r = self._s.post(url, json=body, timeout=60)
            if not r.ok:
                self._log_err("POST", url, r)
                return None
            return r
        except Exception as exc:
            print(f"  [net] POST {url}: {exc}")
            return None

    def _put(self, url: str, body: dict) -> Response | None:
        try:
            r = self._s.put(url, json=body, timeout=30)
            if not r.ok:
                self._log_err("PUT", url, r)
                return None
            return r
        except Exception as exc:
            print(f"  [net] PUT {url}: {exc}")
            return None

    @staticmethod
    def _log_err(method: str, url: str, r: Response) -> None:
        print(f"  [http {r.status_code}] {method} {url.split('?')[0]}: {r.text[:400]}")


# ── Plan builder ──────────────────────────────────────────────────────────────

def build_steps() -> list[Step]:
    """Build the ordered list of process steps from DATA_DIR."""
    steps: list[Step] = []
    session_attrs = {"session": SESSION_ID, "campaign": "MFFD"}

    for i, key in enumerate(PROCESS_CHAIN):
        pred_key = PROCESS_CHAIN[i - 1] if i > 0 else None
        do_name = f"{key.capitalize()}-{SESSION_ID}"
        local_dir = DATA_DIR / key if (DATA_DIR / key).is_dir() else None
        attrs = {**session_attrs, "process_step": key, "step_index": str(i + 1)}

        steps.append(Step(
            key=key,
            do_name=do_name,
            predecessor_key=pred_key,
            local_dir=local_dir,
            attributes=attrs,
        ))

    # Extra subdirs not in PROCESS_CHAIN go in as standalone DataObjects
    if DATA_DIR.is_dir():
        known = set(PROCESS_CHAIN)
        for d in sorted(DATA_DIR.iterdir()):
            if d.is_dir() and d.name not in known:
                steps.append(Step(
                    key=d.name,
                    do_name=f"Other-{d.name}-{SESSION_ID}",
                    predecessor_key=None,
                    local_dir=d,
                    attributes={**session_attrs, "process_step": d.name},
                ))

    return steps


def file_list(directory: Path | None) -> list[Path]:
    if directory is None or not directory.is_dir():
        return []
    return sorted(f for f in directory.rglob("*") if f.is_file())


# ── Dry-run ───────────────────────────────────────────────────────────────────

def dry_run(steps: list[Step]) -> None:
    print("\n=== DRY RUN — no changes ===\n")
    print(f"Collection:   {COLLECTION_NAME!r}")
    print(f"Session ID:   {SESSION_ID!r}")
    print(f"Data dir:     {DATA_DIR.resolve()}")
    print()
    for step in steps:
        chain = f"← {step.predecessor_key}" if step.predecessor_key else "(root)"
        print(f"  DataObject: {step.do_name!r}  {chain}")
        files = file_list(step.local_dir)
        if files:
            total = sum(f.stat().st_size for f in files)
            print(f"    {len(files)} file(s), {_human(total)} total")
            for f in files:
                print(f"    • {f.relative_to(DATA_DIR)}  ({_human(f.stat().st_size)})")
        else:
            print("    (no local files — DataObject created with no payload)")
        print()
    print("Snapshot will be created after import.")


# ── Live import ───────────────────────────────────────────────────────────────

def run(steps: list[Step], client: ShepardClient) -> None:
    # 1. Collection
    print(f"\n[collection] {COLLECTION_NAME!r}")
    coll = client.find_collection(COLLECTION_NAME)
    if coll:
        print(f"  found (id={coll['id']})")
    else:
        print("  creating ...")
        coll = client.create_collection(
            COLLECTION_NAME,
            description=(
                f"MFFD manufacturing dropbox — real process data.\n"
                f"Session {SESSION_ID}. Auto-created by mffd-dropbox-import.py."
            ),
            attrs={"domain": "MFFD", "session": SESSION_ID, "type": "dropbox"},
        )
        if coll is None:
            print("  FAILED — aborting")
            sys.exit(1)
        print(f"  created (id={coll['id']})")

    coll_id: int = coll["id"]
    coll_app_id: str | None = coll.get("appId") or client.get_collection_app_id(coll_id)

    # 2. DataObjects with process chain
    do_ids: dict[str, int] = {}  # key → legacy id (for predecessor links)
    do_app_ids: dict[str, str] = {}  # key → appId (for file uploads)

    for step in steps:
        print(f"\n[step] {step.do_name!r}")
        pred_id = do_ids.get(step.predecessor_key) if step.predecessor_key else None

        existing = client.find_data_object(coll_id, step.do_name)
        if existing:
            print(f"  already exists (id={existing['id']})")
            do = existing
        else:
            do = client.create_data_object(
                coll_id,
                step.do_name,
                description=(
                    f"MFFD {step.key} process step — session {SESSION_ID}.\n"
                    f"Raw data dropbox. Transform via snapshot when ready."
                ),
                attrs=step.attributes,
                predecessor_id=pred_id,
            )
            if do is None:
                print("  FAILED — skipping files for this step")
                continue
            print(f"  created (id={do['id']}, appId={do.get('appId')})")

        do_ids[step.key] = do["id"]
        app_id = do.get("appId")
        if app_id:
            do_app_ids[step.key] = app_id

        # 3. Upload files
        files = file_list(step.local_dir)
        if not files:
            print("  no local files to upload")
            continue

        existing_names = client.list_file_refs(coll_id, do["id"])
        for fp in files:
            display = str(fp.relative_to(DATA_DIR))
            if display in existing_names or fp.name in existing_names:
                print(f"  [skip] {display}")
                continue
            print(f"  [upload] {display}  ({_human(fp.stat().st_size)})")
            if not client.upload_file(app_id or "", fp, display):
                print(f"  [error] upload failed for {display}")

    # 4. Snapshot
    print("\n[snapshot] creating ...")
    if coll_app_id:
        label = f"dropbox-import-{SESSION_ID}"
        snap = client.create_snapshot(coll_app_id, label)
        if snap:
            print(f"  created snapshot: {snap.get('label')}  appId={snap.get('appId')}")
        else:
            print("  WARNING: snapshot creation failed (non-fatal — collection was imported)")
    else:
        print("  WARNING: no collection appId — snapshot skipped")

    print("\n=== Done ===")
    steps_done = [s for s in steps if s.key in do_ids]
    print(f"  {len(steps_done)}/{len(steps)} DataObjects created/found")
    print(f"  Process chain: {' → '.join(s.do_name for s in steps_done)}")


# ── Helpers ───────────────────────────────────────────────────────────────────

def _human(n: int | float) -> str:
    for u in ("B", "KB", "MB", "GB", "TB"):
        if n < 1024:
            return f"{n:.1f} {u}"
        n /= 1024
    return f"{n:.1f} PB"


# ── Entry point ───────────────────────────────────────────────────────────────

def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dry-run", action="store_true", help="Print plan, make no API calls.")
    args = ap.parse_args()

    print(f"[config] SHEPARD_URL     = {SHEPARD_URL}")
    print(f"[config] COLLECTION_NAME = {COLLECTION_NAME!r}")
    print(f"[config] SESSION_ID      = {SESSION_ID!r}")
    print(f"[config] DATA_DIR        = {DATA_DIR.resolve()}")
    if not args.dry_run:
        print(f"[config] SHEPARD_API_KEY = <{'set' if SHEPARD_API_KEY else 'NOT SET — required'}>")

    if not args.dry_run and not SHEPARD_API_KEY:
        print("ERROR: SHEPARD_API_KEY is required.", file=sys.stderr)
        sys.exit(1)

    steps = build_steps()

    if args.dry_run:
        dry_run(steps)
        return

    client = ShepardClient(SHEPARD_URL, SHEPARD_API_KEY)
    run(steps, client)


if __name__ == "__main__":
    main()
