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

A WikiDump DataObject is also created as a placeholder for a manual zip upload.
After all files are uploaded, creates a Collection snapshot so future comparisons
against a clean baseline are possible.

Usage
-----
    # Against nuclide.systems (X-API-KEY):
    SHEPARD_URL=https://shepard.nuclide.systems \\
    SHEPARD_API_KEY=<your-key> \\
    SESSION_ID=2026-05-22-Q1 \\
    DATA_DIR=/path/to/mffd-data \\
    python mffd-dropbox-import.py

    # Against DLR intranet (Bearer JWT):
    SHEPARD_URL=https://backend.bt-au-cube3.intra.dlr.de \\
    SHEPARD_BEARER_TOKEN=eyJhbGciOiJSU... \\
    SESSION_ID=2026-05-22-Q1 \\
    DATA_DIR=/path/to/mffd-data \\
    python mffd-dropbox-import.py

    # Dry-run (no API calls, no log file written):
    python mffd-dropbox-import.py --dry-run

Environment variables
---------------------
    SHEPARD_URL           Base URL.  Default: https://shepard.nuclide.systems
    SHEPARD_API_KEY       X-API-KEY header (nuclide.systems / fork instances).
    SHEPARD_BEARER_TOKEN  Authorization: Bearer token (DLR intranet / JWT tokens).
                          One of the two auth vars must be set for a live run.
    SESSION_ID            Short identifier for this production run.
                          Default: today's date (YYYY-MM-DD).
    DATA_DIR              Directory with subdirs: tapelaying/, bridgewelding/
                          Any unrecognised subdirs go to an "Other-{name}" DataObject
                          with no predecessor link.
    COLLECTION_NAME       Override the collection name.
                          Default: "MFFD-Dropbox"
    LOG_DIR               Directory for the run log file.
                          Default: same directory as the script.

Process chain
-------------
    The script creates DataObjects in this order and links them:

      TapeLaying-{SESSION_ID}     (AFP robot, consolidation force, TCP temp logs)
           ↓  PREDECESSOR_OF
      BridgeWelding-{SESSION_ID}  (UW current, UW force, joint quality logs)

    Additionally, the following DataObjects are created standalone (no chain):
      WikiDump-{SESSION_ID}       Placeholder — user uploads wiki export zip manually.

    Additional subdirs found in DATA_DIR are appended without predecessor links.

Logfile
-------
    All output is tee'd to LOG_DIR/mffd-import-{SESSION_ID}.log so the run is
    fully reproducible.  Open the log file to review any errors after the run.
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
SHEPARD_BEARER_TOKEN = os.environ.get("SHEPARD_BEARER_TOKEN", "")
SESSION_ID = os.environ.get("SESSION_ID", datetime.date.today().isoformat())
DATA_DIR = Path(os.environ.get("DATA_DIR", "."))
COLLECTION_NAME = os.environ.get("COLLECTION_NAME", "MFFD-Dropbox")
LOG_DIR = Path(os.environ.get("LOG_DIR", Path(__file__).parent))

# Canonical MFFD process chain — subdirs that match these names are wired into
# the predecessor chain in the order listed here.
PROCESS_CHAIN = [
    "tapelaying",       # AFP robot run — consolidation force, TCP temp, trajectory logs
    "bridgewelding",    # UW bridge welding — current, force, joint quality
]


# ── Tee logging ───────────────────────────────────────────────────────────────

class Tee:
    """Write to both stdout and a log file simultaneously."""

    def __init__(self, log_path: Path) -> None:
        self._log = open(log_path, "w", buffering=1, encoding="utf-8")
        self._stdout = sys.stdout

    def write(self, msg: str) -> None:
        self._stdout.write(msg)
        self._log.write(msg)

    def flush(self) -> None:
        self._stdout.flush()
        self._log.flush()

    def close(self) -> None:
        self._log.close()


# ── Data model ────────────────────────────────────────────────────────────────

@dataclass
class Step:
    """One MFFD process step DataObject."""
    key: str                  # e.g. "tapelaying"
    do_name: str              # e.g. "TapeLaying-2026-05-22-Q1"
    predecessor_key: str | None
    local_dir: Path | None    # DATA_DIR/<key> if it exists
    standalone: bool = False  # True → not part of the predecessor chain
    attributes: dict[str, str] = field(default_factory=dict)

    @property
    def title(self) -> str:
        return self.do_name.replace("-", " ")


# ── HTTP client ───────────────────────────────────────────────────────────────

class ShepardClient:
    """Minimal Shepard v1/v2 client. Credentials never echoed in output."""

    def __init__(self, base: str, api_key: str, bearer_token: str) -> None:
        self._base = base
        self._s = Session()
        self._s.headers.update({"Accept": "application/json"})
        if bearer_token:
            self._s.headers["Authorization"] = f"Bearer {bearer_token}"
        elif api_key:
            self._s.headers["X-API-KEY"] = api_key

    # ── Warmup ────────────────────────────────────────────────────────────────

    def warmup(self) -> bool:
        """Probe the instance and print user/instance info. Returns True on success."""
        print("\n=== Warmup ===")

        # Current user (v1)
        user_r = self._get(f"{self._base}/shepard/api/users/currentUser")
        if user_r is None:
            print("  [FAIL] Cannot reach /shepard/api/users/currentUser")
            print("         Check SHEPARD_URL and auth credentials.")
            return False
        user = user_r.json()
        username = user.get("username") or user.get("name") or user.get("sub") or "(unknown)"
        email = user.get("email") or ""
        print(f"  user     : {username}  {email}")

        # Collection count (v1) — gives a sense of instance scale
        coll_r = self._get(f"{self._base}/shepard/api/collections", {"page": "0", "size": "1"})
        if coll_r is not None:
            total_count = coll_r.headers.get("X-Total-Count", "?")
            print(f"  instance : {SHEPARD_URL}  (collections visible: {total_count})")

        # Probe v2 availability (fork feature)
        v2_r = self._get(f"{self._base}/v2/admin/features")
        if v2_r is not None:
            features = v2_r.json() if isinstance(v2_r.json(), list) else []
            print(f"  v2 API   : available ({len(features)} feature flags)")
        else:
            print("  v2 API   : not available (v1-only instance — file upload via v1 fallback)")

        print("=== Warmup OK ===\n")
        return True

    # ── Collections (v1) ──────────────────────────────────────────────────────

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

    # ── DataObjects (v1) ──────────────────────────────────────────────────────

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

    # ── Reference payload verification (v1) ───────────────────────────────────

    def verify_references(self, coll_id: int, do_id: int, do_name: str) -> None:
        """Print a summary of all reference payload types on a DataObject."""
        base = f"{self._base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}"
        kinds = [
            ("fileReferences",           "files"),
            ("structuredDataReferences", "structured data"),
            ("timeseriesReferences",     "timeseries"),
        ]
        print(f"  [verify] references on {do_name!r}:")
        for endpoint, label in kinds:
            r = self._get(f"{base}/{endpoint}")
            if r is None:
                print(f"    {label:20s}: (error fetching)")
            else:
                items = r.json() if isinstance(r.json(), list) else []
                if items:
                    names = [i.get("name") or i.get("id") or "?" for i in items[:5]]
                    suffix = f" + {len(items)-5} more" if len(items) > 5 else ""
                    print(f"    {label:20s}: {len(items)} — {', '.join(str(n) for n in names)}{suffix}")
                else:
                    print(f"    {label:20s}: (none)")

    # ── Files ─────────────────────────────────────────────────────────────────

    def list_file_refs(self, coll_id: int, do_id: int) -> set[str]:
        r = self._get(
            f"{self._base}/shepard/api/collections/{coll_id}"
            f"/dataObjects/{do_id}/fileReferences"
        )
        if r is None:
            return set()
        return {ref.get("name", "") for ref in r.json()}

    def upload_file(self, app_id: str, path: Path, display: str,
                    coll_id: int | None = None, do_id: int | None = None) -> bool:
        # Try v2 singleton endpoint first (fork instances)
        url_v2 = f"{self._base}/v2/files"
        params = {"parentDataObjectAppId": app_id, "name": display}
        try:
            with path.open("rb") as fh:
                r = self._s.post(url_v2, params=params, files={"file": (path.name, fh)}, timeout=600)
            if r.status_code not in (404, 405):
                if r.ok:
                    return True
                print(f"  [http {r.status_code}] upload {display}: {r.text[:300]}")
                return False
        except Exception as exc:
            print(f"  [net] v2 upload {path.name}: {exc}")

        # Fallback to v1 multi-part if v2 not available and we have legacy IDs
        if coll_id is not None and do_id is not None:
            url_v1 = (
                f"{self._base}/shepard/api/collections/{coll_id}"
                f"/dataObjects/{do_id}/fileReferences"
            )
            try:
                with path.open("rb") as fh:
                    r = self._s.post(
                        url_v1,
                        files={"file": (display, fh)},
                        timeout=600,
                    )
                if r.ok:
                    return True
                print(f"  [http {r.status_code}] v1 upload {display}: {r.text[:300]}")
                return False
            except Exception as exc:
                print(f"  [net] v1 upload {path.name}: {exc}")

        return False

    # ── Self-upload (provenance) ──────────────────────────────────────────────

    def upload_self(self, coll_id: int, do_id: int, do_app_id: str) -> None:
        """Upload this script file to ImportScripts DataObject as a provenance record."""
        script_path = Path(__file__)
        existing = self.list_file_refs(coll_id, do_id)
        if script_path.name in existing:
            print(f"  [skip] {script_path.name} already in ImportScripts")
            return
        print(f"  [upload] {script_path.name}  ({_human(script_path.stat().st_size)})")
        ok = self.upload_file(do_app_id, script_path, script_path.name, coll_id, do_id)
        if not ok:
            print("  [warn] self-upload failed (non-fatal)")

    # ── Snapshots (v2) ────────────────────────────────────────────────────────

    def create_snapshot(self, coll_app_id: str, label: str) -> dict | None:
        body = {
            "label": label,
            "description": f"Auto-snapshot after MFFD dropbox import — session {SESSION_ID}",
        }
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

    # WikiDump placeholder — standalone, no predecessor, no local upload
    steps.append(Step(
        key="wikidump",
        do_name=f"WikiDump-{SESSION_ID}",
        predecessor_key=None,
        local_dir=None,
        standalone=True,
        attributes={**session_attrs, "process_step": "wikidump", "note": "upload manually — one zip file"},
    ))

    # ImportScripts — stores this script itself as a provenance artifact
    steps.append(Step(
        key="importscripts",
        do_name="ImportScripts",
        predecessor_key=None,
        local_dir=None,
        standalone=True,
        attributes={"type": "toolbox", "note": "ingest scripts — fetch from here to reproduce this run"},
    ))

    # Extra subdirs not in PROCESS_CHAIN or known standalones
    if DATA_DIR.is_dir():
        known = set(PROCESS_CHAIN) | {"wikidump"}
        for d in sorted(DATA_DIR.iterdir()):
            if d.is_dir() and d.name not in known:
                steps.append(Step(
                    key=d.name,
                    do_name=f"Other-{d.name}-{SESSION_ID}",
                    predecessor_key=None,
                    local_dir=d,
                    standalone=True,
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
        if step.standalone:
            chain = "(standalone — no predecessor)"
        elif step.predecessor_key:
            chain = f"← {step.predecessor_key}"
        else:
            chain = "(root)"
        print(f"  DataObject: {step.do_name!r}  {chain}")
        files = file_list(step.local_dir)
        if files:
            total = sum(f.stat().st_size for f in files)
            print(f"    {len(files)} file(s), {_human(total)} total")
            for f in files:
                print(f"    • {f.relative_to(DATA_DIR)}  ({_human(f.stat().st_size)})")
        elif step.key == "wikidump":
            print("    (placeholder — upload one zip manually via UI)")
        else:
            print("    (no local files — DataObject created with no payload)")
        print()
    print("Snapshot will be created after import.")


# ── Live import ───────────────────────────────────────────────────────────────

def run(steps: list[Step], client: ShepardClient) -> None:
    # 0. Warmup / connectivity check
    if not client.warmup():
        sys.exit(1)

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
    do_ids: dict[str, int] = {}
    do_app_ids: dict[str, str] = {}

    for step in steps:
        print(f"\n[step] {step.do_name!r}")
        pred_id = do_ids.get(step.predecessor_key) if step.predecessor_key else None

        existing = client.find_data_object(coll_id, step.do_name)
        if existing:
            print(f"  already exists (id={existing['id']})")
            do = existing
        else:
            if step.key == "wikidump":
                desc = (
                    f"Wiki export placeholder — session {SESSION_ID}.\n"
                    f"Upload one zip file manually via the UI."
                )
            else:
                desc = (
                    f"MFFD {step.key} process step — session {SESSION_ID}.\n"
                    f"Raw data dropbox. Transform via snapshot when ready."
                )
            do = client.create_data_object(
                coll_id,
                step.do_name,
                description=desc,
                attrs=step.attributes,
                predecessor_id=pred_id,
            )
            if do is None:
                print("  FAILED — skipping")
                continue
            print(f"  created (id={do['id']}, appId={do.get('appId')})")

        do_ids[step.key] = do["id"]
        app_id = do.get("appId")
        if app_id:
            do_app_ids[step.key] = app_id

        # 3. Verify existing reference payloads
        client.verify_references(coll_id, do["id"], step.do_name)

        # 4. Upload files (special cases first)
        if step.key == "wikidump":
            print("  [skip] wikidump — upload one zip file manually via UI")
            continue

        if step.key == "importscripts":
            client.upload_self(coll_id, do["id"], app_id or "")
            client.verify_references(coll_id, do["id"], step.do_name)
            continue

        files = file_list(step.local_dir)
        if not files:
            print("  no local files to upload")
            continue

        existing_names = client.list_file_refs(coll_id, do["id"])
        uploaded = 0
        for fp in files:
            display = str(fp.relative_to(DATA_DIR))
            if display in existing_names or fp.name in existing_names:
                print(f"  [skip] {display}")
                continue
            print(f"  [upload] {display}  ({_human(fp.stat().st_size)})")
            ok = client.upload_file(app_id or "", fp, display, coll_id, do["id"])
            if ok:
                uploaded += 1
            else:
                print(f"  [error] upload failed for {display}")

        if uploaded:
            # Re-verify after uploads to confirm references landed
            print(f"  [verify-post-upload] {uploaded} file(s) uploaded — re-checking refs ...")
            client.verify_references(coll_id, do["id"], step.do_name)

    # 5. Snapshot
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
    chain_steps = [s for s in steps if not s.standalone and s.key in do_ids]
    standalone_steps = [s for s in steps if s.standalone and s.key in do_ids]
    print(f"  Process chain: {' → '.join(s.do_name for s in chain_steps)}")
    if standalone_steps:
        print(f"  Standalone:    {', '.join(s.do_name for s in standalone_steps)}")


# ── Helpers ───────────────────────────────────────────────────────────────────

def _human(n: int | float) -> str:
    for u in ("B", "KB", "MB", "GB", "TB"):
        if n < 1024:
            return f"{n:.1f} {u}"
        n /= 1024
    return f"{n:.1f} PB"


# ── Entry point ───────────────────────────────────────────────────────────────

def main() -> None:
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    ap.add_argument("--dry-run", action="store_true", help="Print plan, make no API calls.")
    args = ap.parse_args()

    steps = build_steps()

    if args.dry_run:
        print(f"[config] SHEPARD_URL     = {SHEPARD_URL}")
        print(f"[config] COLLECTION_NAME = {COLLECTION_NAME!r}")
        print(f"[config] SESSION_ID      = {SESSION_ID!r}")
        print(f"[config] DATA_DIR        = {DATA_DIR.resolve()}")
        dry_run(steps)
        return

    # Auth check
    if not SHEPARD_API_KEY and not SHEPARD_BEARER_TOKEN:
        print("ERROR: set SHEPARD_API_KEY or SHEPARD_BEARER_TOKEN.", file=sys.stderr)
        sys.exit(1)

    # Start logging to file
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    log_path = LOG_DIR / f"mffd-import-{SESSION_ID}.log"
    tee = Tee(log_path)
    sys.stdout = tee  # type: ignore[assignment]

    print(f"[config] SHEPARD_URL     = {SHEPARD_URL}")
    print(f"[config] COLLECTION_NAME = {COLLECTION_NAME!r}")
    print(f"[config] SESSION_ID      = {SESSION_ID!r}")
    print(f"[config] DATA_DIR        = {DATA_DIR.resolve()}")
    auth_mode = "Bearer token" if SHEPARD_BEARER_TOKEN else "X-API-KEY"
    print(f"[config] auth            = {auth_mode}")
    print(f"[config] log file        = {log_path}")

    client = ShepardClient(SHEPARD_URL, SHEPARD_API_KEY, SHEPARD_BEARER_TOKEN)
    run(steps, client)

    tee.close()
    sys.stdout = tee._stdout  # type: ignore[attr-defined]
    print(f"\nLog written to: {log_path}")


if __name__ == "__main__":
    main()
